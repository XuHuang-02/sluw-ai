"""Offline reference validator for the EDD contract. No service, DB or LLM calls.
Production Java validation must enforce the same schema and semantic checks.
"""
from pathlib import Path
from datetime import datetime
import json
import sys
from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = json.loads((ROOT/'src/main/resources/edd/contracts/v1/edd.schema.json').read_text(encoding='utf-8'))
MAX_BYTES = 16 * 1024 * 1024

def issue(code, path, message):
    return {'code': code, 'path': path, 'message': message}

def parse_payload(raw):
    if len(raw) > MAX_BYTES:
        return None, [issue('PAYLOAD_TOO_LARGE', '', 'JSON payload exceeds 16 MiB')]
    def object_pairs(pairs):
        value = {}
        for key, item in pairs:
            if key in value:
                raise ValueError('Duplicate JSON object key')
            value[key] = item
        return value
    try:
        return json.loads(raw, object_pairs_hook=object_pairs,
                          parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Non-finite number'))), []
    except (ValueError, UnicodeError):
        return None, [issue('INVALID_JSON', '', 'Malformed JSON, duplicate key or invalid number')]

def validate(value, kind='request', request=None, allowed_rule_ids=()):
    if kind not in SCHEMA['$defs']:
        raise ValueError('Unknown contract kind')
    if isinstance(value, dict) and 'schema_version' in value and value['schema_version'] != '1.0':
        return [issue('UNSUPPORTED_SCHEMA_VERSION', '/schema_version', 'Only version 1.0 is accepted')]
    schema = {**SCHEMA, '$ref': '#/$defs/' + kind}
    errors = [issue('INVALID_REQUEST', '/' + '/'.join(str(p) for p in e.absolute_path), e.message)
              for e in Draft202012Validator(schema, format_checker=FormatChecker()).iter_errors(value)]
    if errors:
        return errors
    if kind == 'request':
        facts = {}
        for group in ('core_snapshots', 'events', 'risk_records', 'manual_excerpts'):
            for index, fact in enumerate(value[group]):
                fid = fact['fact_id']
                if fid in facts:
                    errors.append(issue('DUPLICATE_ID', f'/{group}/{index}/fact_id', 'Duplicate fact ID'))
                facts[fid] = fact
        for group, key in [('attachments', 'attachment_id'), ('conflicts', 'conflict_id')]:
            ids = [x[key] for x in value[group]]
            if len(ids) != len(set(ids)):
                errors.append(issue('DUPLICATE_ID', '/'+group, 'Duplicate ID'))
        attachments = {x['attachment_id'] for x in value['attachments']}
        for i, excerpt in enumerate(value['manual_excerpts']):
            if excerpt['attachment_id'] is not None and excerpt['attachment_id'] not in attachments:
                errors.append(issue('INVALID_REFERENCE', f'/manual_excerpts/{i}/attachment_id', 'Unknown attachment'))
            if excerpt['confirmed'] and excerpt['confirmed_at'] is None:
                errors.append(issue('INVALID_REQUEST', f'/manual_excerpts/{i}/confirmed_at', 'Confirmation time required'))
        for i, conflict in enumerate(value['conflicts']):
            if not set(conflict['candidate_fact_ids']) <= facts.keys():
                errors.append(issue('INVALID_REFERENCE', f'/conflicts/{i}/candidate_fact_ids', 'Unknown candidate'))
            adopted = conflict['adopted_fact_id']
            if adopted is not None:
                if adopted not in conflict['candidate_fact_ids'] or adopted not in facts:
                    errors.append(issue('INVALID_REFERENCE', f'/conflicts/{i}/adopted_fact_id', 'Adopted fact must be a candidate'))
                elif facts[adopted].get('confirmed') is False:
                    errors.append(issue('INVALID_REFERENCE', f'/conflicts/{i}/adopted_fact_id', 'Unconfirmed excerpt cannot be adopted'))
        for name, coverage in value['coverage'].items():
            if coverage['from'] is not None and coverage['to'] is not None:
                if datetime.fromisoformat(coverage['from'].replace('Z', '+00:00')) > datetime.fromisoformat(coverage['to'].replace('Z', '+00:00')):
                    errors.append(issue('INVALID_REQUEST', '/coverage/'+name, 'Coverage interval is reversed'))
    elif kind == 'result':
        if request is None:
            return [issue('VALIDATION_CONTEXT_REQUIRED', '', 'Result validation requires the original request')]
        if validate(request):
            return [issue('VALIDATION_CONTEXT_REQUIRED', '', 'Original request must satisfy the contract')]
        facts = {x['fact_id']: x for group in ('core_snapshots','events','risk_records','manual_excerpts') for x in request[group]}
        evidence = {x['fact_id']: x for x in value['evidence_index']}
        if len(evidence) != len(value['evidence_index']):
            errors.append(issue('DUPLICATE_ID', '/evidence_index', 'Duplicate evidence fact ID'))
        for fid, entry in evidence.items():
            if fid not in facts or entry['source'] != facts[fid]['source']:
                errors.append(issue('INVALID_REFERENCE', '/evidence_index', 'Evidence must match original source'))
        def walk(node, path=''):
            if isinstance(node, dict):
                for key, val in node.items():
                    if key == 'fact_refs' and not set(val) <= (facts.keys() & evidence.keys()):
                        errors.append(issue('INVALID_REFERENCE', path+'/'+key, 'Fact citation is not backed by input and evidence'))
                    elif key == 'rule_refs' and not set(val) <= set(allowed_rule_ids):
                        errors.append(issue('INVALID_REFERENCE', path+'/'+key, 'Rule citation is not in server-approved rules'))
                    else:
                        walk(val, path+'/'+key)
            elif isinstance(node, list):
                for i, val in enumerate(node): walk(val, path+'/'+str(i))
        walk(value)
        original_conflicts = {x['conflict_id']: x for x in request['conflicts']}
        for conflict in value['conflicts']:
            if conflict != original_conflicts.get(conflict['conflict_id']):
                errors.append(issue('INVALID_REFERENCE', '/conflicts', 'Result must preserve input conflicts'))
        if {x['conflict_id'] for x in value['conflicts']} != set(original_conflicts):
            errors.append(issue('INVALID_REFERENCE', '/conflicts', 'Result cannot omit input conflicts'))
        for field, expected in [('input_version', request['snapshot_id']), ('parent_analysis_id', request['parent_analysis_id']), ('synthetic', request['synthetic'])]:
            if value[field] != expected:
                errors.append(issue('INVALID_REFERENCE', '/'+field, 'Result does not match input version'))
        for key in ('grade_recommendation', 'report_recommendation'):
            rec = value[key]
            if rec['value'] is not None and (value['rule_version'] is None or not rec['rule_refs']):
                errors.append(issue('INVALID_REQUEST', '/'+key, 'Determinate recommendation needs versioned approved rules'))
        if value['status'] == 'completed' and (value['missing_items'] or any(not x['resolved'] for x in value['conflicts'])):
            errors.append(issue('INVALID_REQUEST', '/status', 'Missing or conflicting evidence requires completed_with_gaps'))
    return errors

if __name__ == '__main__':
    if len(sys.argv) not in (2, 3):
        raise SystemExit('Usage: python validate_contract.py request.json [request|accepted|task|error]')
    data, errors = parse_payload(Path(sys.argv[1]).read_bytes())
    if not errors: errors = validate(data, sys.argv[2] if len(sys.argv) == 3 else 'request')
    # Validation messages can contain values; use synthetic examples only in CLI.
    print(json.dumps({'valid': not errors, 'errors': errors}, ensure_ascii=False, indent=2))
    raise SystemExit(1 if errors else 0)
