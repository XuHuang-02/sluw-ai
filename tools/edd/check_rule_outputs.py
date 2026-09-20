"""Check actual Java recommendation fragments for all 32 no-formal-rule scenarios."""
import json
from pathlib import Path
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]

def main():
    schema = json.loads((ROOT / "src/main/resources/edd/contracts/v1/edd.schema.json").read_text(encoding="utf-8"))
    ids = [f"SYN-V1-{i:03d}" for i in range(1, 23)] + [f"DB-{i:03d}" for i in range(1, 11)]
    for case_id in ids:
        path = ROOT / "target/edd-rule-output" / f"{case_id}.json"
        if not path.exists():
            raise SystemExit("Run EddRuleServiceTest first: " + str(path))
        output = json.loads(path.read_text(encoding="utf-8"))
        for name in ("grade", "report"):
            fragment = {"$schema": schema["$schema"], "$defs": schema["$defs"], "$ref": f"#/$defs/{name}"}
            Draft202012Validator(fragment).validate(output[name])
            if output[name]["value"] is not None:
                raise AssertionError((case_id, name, "No formal package must produce a null suggestion"))
    print("PASS: 32 actual Java grade/report outputs conform to v1 and remain undetermined without formal rules.")

if __name__ == "__main__":
    main()
