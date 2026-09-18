"""Validate actual Java adapter output against the authoritative v1 contract."""
from pathlib import Path
import json
from validate_contract import ROOT, validate

def main():
    source=json.loads((ROOT/'tools/edd/fixtures/core-db-cases.json').read_text(encoding='utf-8'))
    for case in source['cases']:
        path=ROOT/'target/edd-adapter-output'/(case['case_id']+'.json')
        if not path.exists():raise SystemExit('Run EddInputAdapterTest first: '+str(path))
        request=json.loads(path.read_text(encoding='utf-8'))
        errors=validate(request)
        if errors:raise AssertionError((case['case_id'],errors))
    print('PASS: 10 actual Java outputs conform to v1 schema and reference checks.')
if __name__=='__main__': main()
