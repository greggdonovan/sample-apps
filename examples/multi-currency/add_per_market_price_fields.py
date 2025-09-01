import json

items = []
with open("items.jsonl", "r", encoding="utf-8") as f:
    for line in f:
        if line.strip():
            items.append(json.loads(line))

for item in items:
    fields = item["fields"]

    # ensure per_market_price exists as a list
    if "per_market_price" not in fields or not isinstance(fields["per_market_price"], list):
        fields["per_market_price"] = []

    # extract native market code
    native_market = fields["currency_ref"].split("::")[-1].lower()
    native_price = fields["price"]

    # only add if not already there
    if not any(p["market"] == native_market for p in fields["per_market_price"]):
        fields["per_market_price"].append({"market": native_market, "price": native_price})

with open("items.jsonl", "w", encoding="utf-8") as f:
    for item in items:
        f.write(json.dumps(item) + "\n")

