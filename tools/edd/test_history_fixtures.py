"""Portable scenario subsets must remain valid v1 requests."""
import json
from pathlib import Path
import unittest
from validate_contract import validate

class HistoryFixtureContractTest(unittest.TestCase):
    def test_preparation_and_history_fixtures(self):
        root = Path(__file__).parent / "fixtures"
        for name in ("fact-scope-cases.json", "history-cases.json", "rule-extra-cases.json"):
            for case in json.loads((root / name).read_text(encoding="utf-8"))["cases"]:
                with self.subTest(file=name, case=case["case_id"]):
                    self.assertEqual([], validate(case["input"]))

if __name__ == "__main__":
    unittest.main()
