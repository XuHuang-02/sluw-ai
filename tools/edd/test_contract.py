import unittest
import json
from copy import deepcopy
from validate_contract import ROOT, SCHEMA, validate, parse_payload
from jsonschema import Draft202012Validator
E = ROOT/'docs/edd/examples'
def load(name): return json.loads((E/name).read_text(encoding='utf-8'))

class ContractTest(unittest.TestCase):
    def setUp(self): self.request = load('request-minimal.json')
    def test_schema_is_valid(self): Draft202012Validator.check_schema(SCHEMA)
    def test_missing_evidence_is_valid(self): self.assertEqual([], validate(self.request))
    def test_confirmed_conflict(self): self.assertEqual([], validate(load('request-confirmed-conflict.json')))
    def test_invalid_examples(self):
        for name, code in [('missing-field','INVALID_REQUEST'),('wrong-type','INVALID_REQUEST'),('unknown-version','UNSUPPORTED_SCHEMA_VERSION'),('invalid-reference','INVALID_REFERENCE')]:
            with self.subTest(name=name): self.assertIn(code, [x['code'] for x in validate(load('invalid-'+name+'.json'))])
    def test_timezone_required(self):
        self.request['analysis_as_of']='2026-09-18T09:00:00';self.assertTrue(validate(self.request))
    def test_real_calendar_required(self):
        self.request['analysis_as_of']='2026-02-30T09:00:00Z';self.assertTrue(validate(self.request))
    def test_unknown_role_rejected(self):
        self.request['context']['role']='handler';self.assertTrue(validate(self.request))
    def test_cannot_claim_rules_approved(self):
        self.request['rule_context']['approved_rating_rules_available']=True;self.assertTrue(validate(self.request))
    def test_failed_query_cannot_be_complete(self):
        self.request['coverage']['actual_payments']['complete']=True;self.assertTrue(validate(self.request))
    def test_duplicate_fact(self):
        r=load('request-confirmed-conflict.json');r['manual_excerpts'][1]['fact_id']='F1';self.assertIn('DUPLICATE_ID',[x['code'] for x in validate(r)])
    def test_missing_attachment(self):
        r=load('request-confirmed-conflict.json');r['manual_excerpts'][0]['attachment_id']='missing';self.assertTrue(validate(r))
    def test_unconfirmed_adoption(self):
        r=load('request-confirmed-conflict.json');r['manual_excerpts'][0]['confirmed']=False;self.assertTrue(validate(r))
    def test_reversed_coverage(self):
        c=self.request['coverage']['actual_payments'];c['from']='2026-01-02T00:00:00Z';c['to']='2026-01-01T00:00:00Z';self.assertTrue(validate(self.request))
    def test_response_examples(self):
        for n in ['accepted','task','error']:
            with self.subTest(kind=n): self.assertEqual([],validate(load({'task':'task-queued.json'}.get(n,n+'.json')),n))
    def test_result_with_gaps(self): self.assertEqual([],validate(load('result-with-gaps.json'),'result',self.request))
    def test_fake_fact_reference(self):
        x=load('result-with-gaps.json');x['overview_sections']=[{'section':'risk','text':'x','fact_refs':['fake'],'rule_refs':[]}];self.assertTrue(validate(x,'result',self.request))
    def test_cannot_grade_without_rules(self):
        x=load('result-with-gaps.json');x['grade_recommendation'].update(status='suggestion',value='high');self.assertTrue(validate(x,'result',self.request))
    def test_unknown_cannot_have_value(self):
        x=load('result-with-gaps.json');x['report_recommendation']['value']='suggest_not_report';self.assertTrue(validate(x,'result',self.request))
    def test_input_version_mismatch(self):
        x=load('result-with-gaps.json');x['input_version']='another';self.assertTrue(validate(x,'result',self.request))
    def test_completed_cannot_hide_gaps(self):
        x=load('result-with-gaps.json');x['status']='completed';self.assertTrue(validate(x,'result',self.request))
    def test_invalid_terminal_state(self):
        x=load('task-queued.json');x['status']='failed';self.assertTrue(validate(x,'task'))
    def test_parser(self):
        for raw in [b'{',b'{"x":1,"x":2}',b'{"x":NaN}',b'\xff']:
            with self.subTest(raw=raw): self.assertEqual('INVALID_JSON',parse_payload(raw)[1][0]['code'])
    def test_payload_limit(self): self.assertEqual('PAYLOAD_TOO_LARGE',parse_payload(b' '*(16*1024*1024+1))[1][0]['code'])
    def test_money_is_decimal_string(self):
        metric={'name':'premium','value':100.0,'unit':None,'currency_code':None,'currency_label':None,'grain':'policy','fact_refs':[]}
        self.assertTrue(validate(metric,'metric'));metric['value']='100.00';self.assertEqual([],validate(metric,'metric'))

    def test_suggestion_needs_a_value(self):
        x=load('result-with-gaps.json');x['grade_recommendation']['status']='suggestion';self.assertTrue(validate(x,'result',self.request))
    def test_source_cannot_be_forged(self):
        req=load('request-confirmed-conflict.json');x=load('result-with-gaps.json');x['conflicts']=req['conflicts']
        x['evidence_index']=[{'fact_id':'F1','source':deepcopy(req['manual_excerpts'][0]['source'])}]
        self.assertEqual([],validate(x,'result',req))
        x['evidence_index'][0]['source']['source_id']='FORGED';self.assertTrue(validate(x,'result',req))
    def test_conflict_cannot_be_hidden(self):
        req=load('request-confirmed-conflict.json');x=load('result-with-gaps.json');self.assertTrue(validate(x,'result',req))
    def test_forged_rule_reference(self):
        x=load('result-with-gaps.json');x['grade_recommendation'].update(status='suggestion',value='high',rule_refs=['FAKE']);x['rule_version']='FAKE-VERSION'
        self.assertTrue(validate(x,'result',self.request))
    def test_all_fixture_paths_exist(self):
        manifest=json.loads((ROOT/'docs/edd/fixture-routing.json').read_text(encoding='utf-8'))
        self.assertEqual(32,len(manifest['cases']))
        for route in manifest['cases']:
            if route['status'] == 'adapter_pending':
                # Legacy design-only cases are not runtime fixtures and are not distributed.
                continue
            self.assertEqual('implemented_and_validated', route['status'])
            source=ROOT/route['file']
            self.assertTrue(source.exists(), 'Required implemented fixture missing: '+str(source))
            payload=json.loads(source.read_text(encoding='utf-8-sig'))
            index=int(route['pointer'].split('/')[2]);self.assertEqual(route['case_id'],payload['cases'][index]['case_id']);self.assertIn('input',payload['cases'][index])

if __name__=='__main__': unittest.main()
