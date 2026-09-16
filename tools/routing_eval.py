#!/usr/bin/env python3
"""Portable routing comparison runner. Python 3.9+, standard library only.
prepare never calls a model. run requires --execute and a reviewed case manifest.
Append-only intent/result journal: resume NEVER repeats a possibly sent call.
"""
import argparse, collections, datetime, hashlib, json, os, pathlib, statistics, time
import urllib.request, urllib.error, urllib.parse
ROOT = pathlib.Path(__file__).resolve().parents[1]
DEFAULT_CASES = ROOT / "docs/trial/deal-issue-cases.json"
MODES = ("CONDITIONS", "RECORDS")
def digest(data): return hashlib.sha256(data).hexdigest()
def read(path): return json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
def write(path, value): pathlib.Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
def stamp(): return datetime.datetime.now(datetime.timezone.utc).isoformat()
def cases_at(path):
    data = pathlib.Path(path).read_bytes(); cases = json.loads(data)
    ids = [c["id"] for c in cases]
    if len(ids) != len(set(ids)): raise ValueError("duplicate case IDs")
    for c in cases:
        if c["expectedStatus"] not in ("SELECTED", "INSUFFICIENT") or not c["expectedBranch"]: raise ValueError("invalid expected result")
    return digest(data), {c["id"]: c for c in cases}
def prepare(args):
    folder = pathlib.Path(args.out); folder.mkdir(parents=True, exist_ok=True)
    if (folder / "plan.json").exists(): raise ValueError("plan exists; use a new output directory")
    sha, cases = cases_at(args.cases)
    tasks = []
    for repeat in range(args.repeats):
        for index, case in enumerate(cases):
            order = MODES if (repeat + index) % 2 == 0 else MODES[::-1]
            for mode in order:
                tasks.append({"key": f"{case}/{repeat + 1}/{mode}", "caseId": case, "repeat": repeat + 1, "mode": mode})
    write(folder / "plan.json", {"createdAt": stamp(), "casesSha256": sha, "runnerSha256": digest(pathlib.Path(__file__).read_bytes()), "tasks": tasks})
    write(folder / "review.json", {"casesSha256": sha, "approved": False, "reviewer": "", "reviewedAt": "", "note": "Review every expectedStatus/expectedBranch against business code before approval."})
    print(f"Prepared {len(tasks)} calls; no network requests. Review {folder / 'review.json'}.")
def journal(folder):
    path = folder / "results.jsonl"; latest = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                record = json.loads(line); latest[record["key"]] = record
    return latest
def append(folder, record):
    with (folder / "results.jsonl").open("a", encoding="utf-8") as f:
        f.write(json.dumps(record, ensure_ascii=False) + "\n"); f.flush(); os.fsync(f.fileno())
def cost(result, prices):
    if not prices or result.get("model") != prices.get("model"): return None
    a, b = result.get("inputTokens"), result.get("outputTokens")
    if a is None or b is None: return None
    return (a * prices["inputPerMillion"] + b * prices["outputPerMillion"]) / 1000000

def summarize(folder, prices=None):
    plan = read(folder / "plan.json"); latest = journal(folder); summaries = {}
    for mode in MODES:
        rows = [v for v in latest.values() if v["mode"] == mode]
        received = [r for r in rows if isinstance(r.get("result"), dict)]
        invoked = [r for r in received if r["result"].get("modelCallStarted") is True]
        unknown = [r for r in rows if r.get("delivery") != "RECEIVED"]
        categories = collections.Counter()
        correct = 0
        for r in received:
            result = r["result"]; selection = result.get("selection") or {}
            target = selection.get("branchId") if selection.get("status") == "SELECTED" else selection.get("blockedAt")
            if target == r["expectedBranch"] and selection.get("status") == r["expectedStatus"]:
                if result.get("modelCallStarted"): correct += 1
            categories[result.get("outcome", "UNKNOWN")] += 1
        def count_rate(predicate):
            count = sum(predicate(r["result"]) for r in invoked)
            return {"count": count, "rateAmongConfirmedCalls": count / len(invoked) if invoked else None}
        costs = [cost(r["result"], prices) for r in invoked]
        def metric(field):
            values = [r["result"][field] for r in invoked if r["result"].get(field) is not None]
            return {"reportedCount": len(values), "mean": statistics.mean(values) if values else None, "sum": sum(values) if values else None}
        summaries[mode] = {
            "planned": sum(t["mode"] == mode for t in plan["tasks"]), "attemptedHttp": len(rows),
            "modelCallsConfirmed": len(invoked), "deliveryUnknown": len(unknown),
            "beforeCallFailures": sum(not r["result"].get("modelCallStarted") for r in received),
            "missingModelMetadata": sum(not isinstance(r["result"].get("model"),str) or not r["result"]["model"].strip() for r in invoked),
            "validCorrect": correct, "validCorrectRateAmongConfirmedCalls": correct / len(invoked) if invoked else None,
            "formatFailures": count_rate(lambda v: v.get("outcome") in ("MODEL_OUTPUT_REJECTED", "EMPTY_MODEL_OUTPUT") and v.get("selection") is None),
            "routingRejections": count_rate(lambda v: v.get("outcome") == "MODEL_OUTPUT_REJECTED" and v.get("selection") is not None),
            "callFailures": count_rate(lambda v: v.get("outcome") in ("MODEL_CALL_FAILED", "RESPONSE_TIMEOUT")),
            "outcomes": dict(categories), "rejectionReasons": dict(collections.Counter(r["result"].get("reason") for r in received if r["result"].get("outcome") == "MODEL_OUTPUT_REJECTED")),
            "callMs": metric("callMs"), "elapsedMs": metric("elapsedMs"),
            "inputTokens": metric("inputTokens"), "outputTokens": metric("outputTokens"), "totalTokens": metric("totalTokens"),
            "knownEstimatedCost": sum(v for v in costs if v is not None) if any(v is not None for v in costs) else None,
            "unknownCostCalls": sum(v is None for v in costs) + len(unknown), "currency": prices.get("currency") if prices else None,
            "note": "Unknown delivery is excluded from the computable denominator; do not claim a complete rate until reconciled. Timeout usage may arrive only in server logs."
        }
    write(folder / "summary.json", {"updatedAt": stamp(), "modes": summaries})
    print(json.dumps(summaries, ensure_ascii=False, indent=2))
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl): return None

def run(args):
    folder = pathlib.Path(args.out); plan = read(folder / "plan.json"); review = read(folder / "review.json")
    sha, cases = cases_at(args.cases)
    if sha != plan["casesSha256"] or sha != review.get("casesSha256"): raise ValueError("case file changed")
    if digest(pathlib.Path(__file__).read_bytes()) != plan["runnerSha256"]: raise ValueError("runner changed; prepare a new plan")
    if not review.get("approved") or not review.get("reviewer") or not review.get("reviewedAt"): raise ValueError("human case review not recorded")
    if not args.execute: raise ValueError("run needs --execute; this performs billable model calls")
    parsed = urllib.parse.urlparse(args.base_url)
    if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment: raise ValueError("invalid base URL")
    if parsed.scheme == "http" and parsed.hostname not in ("localhost", "127.0.0.1", "::1"): raise ValueError("use HTTPS for non-local services")
    cookie = os.environ.get("SLUW_EVAL_COOKIE")
    if not cookie: raise ValueError("set SLUW_EVAL_COOKIE to your logged-in browser session Cookie; it is never saved")
    opener = urllib.request.build_opener(NoRedirect)
    binding_path = folder / "environment.json"
    if binding_path.exists():
        binding = read(binding_path)
        if binding["baseUrl"] != args.base_url or binding["expectedModel"] != args.model: raise ValueError("environment changed; use new plan")
    else:
        binding = {"baseUrl": args.base_url, "expectedModel": args.model, "prompts": {}, "version": None}
        write(binding_path, binding)
    completed = journal(folder)
    for task in plan["tasks"]:
        if task["key"] in completed: continue  # Includes intents interrupted before receiving a response.
        case = cases[task["caseId"]]
        record = dict(task, expectedBranch=case["expectedBranch"], expectedStatus=case["expectedStatus"], startedAt=stamp(), delivery="PENDING")
        append(folder, record)
        req = urllib.request.Request(args.base_url.rstrip("/") + "/api/agent/trial/evaluate",
            data=json.dumps({"question": json.dumps(case["input"], ensure_ascii=False), "mode": task["mode"]}, ensure_ascii=False).encode(),
            headers={"Content-Type": "application/json", "Accept": "application/json", "Cookie": cookie}, method="POST")
        start = time.monotonic()
        try:
            with opener.open(req, timeout=args.timeout) as response: result = json.load(response)
            if not isinstance(result, dict) or result.get("mode") != task["mode"] or "modelCallStarted" not in result: raise ValueError("unexpected response contract")
            # Missing provider metadata is not evidence of a different model. Keep it unknown.
            observed = {key: result.get(key) for key in ("model", "promptHash", "version")}
            missing = [key for key, value in observed.items() if value is None or isinstance(value,str) and not value.strip()]
            changed = []
            for key, value in observed.items():
                if key not in missing and not isinstance(value,str): changed.append(key + "_invalid_type")
            if "model" not in missing and observed["model"] != args.model: changed.append("model")
            prompt = observed["promptHash"]; mode = task["mode"]; version = observed["version"]
            if "promptHash" not in missing and mode in binding["prompts"] and binding["prompts"][mode] != prompt: changed.append("promptHash:" + mode)
            if "version" not in missing and binding["version"] not in (None, version): changed.append("version")
            record.update(delivery="RECEIVED", result=result, environmentCheck={"missing":missing,"changed":changed}, httpMs=round((time.monotonic()-start)*1000)); append(folder, record)
            print(task["key"], result.get("outcome"), flush=True)
            if changed: raise RuntimeError("environment changed: " + ", ".join(changed) + "; result saved, baseline unchanged, stopped for review")
            if "promptHash" not in missing: binding["prompts"][mode] = prompt
            if "version" not in missing: binding["version"] = version
            write(binding_path, binding)
            if not result.get("modelCallStarted") or result.get("outcome") in ("RESPONSE_TIMEOUT", "MODEL_CALL_FAILED", "SERVER_ERROR"):
                raise RuntimeError("call failed or timed out; result saved, stopped without retry")
        except RuntimeError: raise
        except Exception as error:
            # Do not print exception text: URL/headers/provider response may contain sensitive values.
            record.update(delivery="UNKNOWN", errorType=type(error).__name__, httpMs=round((time.monotonic()-start)*1000)); append(folder, record)
            raise RuntimeError("transport/auth/response failure; stopped without retry: " + type(error).__name__) from None
    summarize(folder)

def upgrade_runner(args):
    """Only migrate the known original runner; preserve all calls and frozen environment."""
    folder = pathlib.Path(args.out)
    lock = folder / "run.lock"
    with lock.open("x", encoding="utf-8") as handle: handle.write(str(os.getpid()))
    try:
        plan = read(folder / "plan.json")
        sha, _ = cases_at(args.cases)
        if sha != plan["casesSha256"]: raise ValueError("case file changed; runner upgrade refused")
        current = digest(pathlib.Path(__file__).read_bytes())
        previous = plan["runnerSha256"]
        if previous == current:
            print("Runner already current; no changes."); return
        supported = ('57b6a7ea72519736113375d16adc2b839c047165093d489cda3e3ad0ac7ba64c', '6511519b14fd9854dd1cb89d760dd53e039bd5d13fa0e6e09012189681e75ccb')
        if previous not in supported: raise ValueError("unknown runner version; migration refused")
        # Read the journal before changing the plan; corrupted results must not be bypassed.
        count = len(journal(folder))
        with (folder / "runner-upgrades.jsonl").open("a", encoding="utf-8") as handle:
            handle.write(json.dumps({"at":stamp(),"from":previous,"to":current,"preservedTasks":count,
                "reason":"empty model metadata is unknown, not drift; explicit diagnostics"}) + "\n")
            handle.flush(); os.fsync(handle.fileno())
        plan["runnerSha256"] = current
        temporary = folder / "plan.upgrade.tmp"
        write(temporary, plan); temporary.replace(folder / "plan.json")
        print(f"Runner upgraded; preserved {count} attempted tasks. No model calls made.")
    finally: lock.unlink()

def main():
    parser = argparse.ArgumentParser(description=__doc__); sub = parser.add_subparsers(dest="command", required=True)
    for name in ("prepare", "run", "summarize", "upgrade-runner"):
        p = sub.add_parser(name); p.add_argument("--out", required=True)
        if name != "summarize": p.add_argument("--cases", default=str(DEFAULT_CASES))
        if name == "prepare": p.add_argument("--repeats", type=int, default=5)
        if name == "run":
            p.add_argument("--base-url", default="http://localhost:8089"); p.add_argument("--model", required=True)
            p.add_argument("--timeout", type=int, default=90); p.add_argument("--execute", action="store_true")
        if name == "summarize": p.add_argument("--prices", help="optional reviewed flat input/output prices per million tokens")
    args = parser.parse_args()
    if args.command == "prepare":
        if args.repeats < 1: parser.error("repeats must be positive")
        prepare(args)
    elif args.command == "upgrade-runner": upgrade_runner(args)
    elif args.command == "run":
        lock = pathlib.Path(args.out) / "run.lock"
        with lock.open("x", encoding="utf-8") as handle: handle.write(str(os.getpid()))
        try: run(args)
        finally:
            lock.unlink()
            if (pathlib.Path(args.out)/"plan.json").exists(): summarize(pathlib.Path(args.out))
    else:
        prices = read(args.prices) if args.prices else None
        if prices and (not prices.get("model") or not prices.get("currency") or any(not isinstance(prices.get(k), (int,float)) or prices[k] < 0 for k in ("inputPerMillion", "outputPerMillion"))): raise ValueError("invalid price config")
        summarize(pathlib.Path(args.out), prices)
if __name__ == "__main__":
    try: main()
    except Exception as error: raise SystemExit(str(error))
