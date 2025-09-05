import sys
import xml.etree.ElementTree as ET
import json


def parse_currencies(root) -> set[str]:
    currencies = []
    for currency in root.findall('.//currency'):
        currencies.append(currency.get('code').lower())

    return sorted(currencies)


def convert_currency_xml_to_vespa_jsonl(xml_file) -> list[str]:
    # Parse the XML file
    tree = ET.parse(xml_file)
    root = tree.getroot()

    currencies = parse_currencies(root)

    rate_map = {}
    for r in root.findall('.//rate'):
        f = r.get('from')
        t = r.get('to')
        rate = float(r.get('rate'))
        rate_map.setdefault(f.lower(), {})[t.lower()] = rate

    usd_factors = {"usd": 1.0}
    for r in root.findall('.//rate[@to="USD"]'):
        usd_factors[r.get('from').lower()] = float(r.get('rate'))

    # # Add USD to USD conversion (factor = 1.0)
    # usd_doc = {
    #     "put": "id:shopping:currency::usd",
    #     "fields": {"factor": 1.0}
    # }
    # currency_rates = [json.dumps(usd_doc) + '\n']

    currency_rates = []
    for code in currencies:
        # Create Vespa document
        doc = {
            "put": f"id:shopping:currency::{code}",
            "fields": {
                "code": code,
                "idx": currencies.index(code),
                "factor": usd_factors[code],
                "factor_map": rate_map[code],
            }
        }

        currency_rates.append(json.dumps(doc))

    return currency_rates

# Usage
if __name__ == "__main__":
    currency_docs = convert_currency_xml_to_vespa_jsonl('currency.xml')
    sys.stdout.write("\n".join(currency_docs) + "\n")
