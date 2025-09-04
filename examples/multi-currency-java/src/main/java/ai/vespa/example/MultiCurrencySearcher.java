package ai.vespa.example;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.RangeItem;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


@After("ExternalYql")
public class MultiCurrencySearcher extends Searcher {

    static final CompoundName MIN_PRICE = new CompoundName("min-price");
    static final CompoundName MAX_PRICE = new CompoundName("max-price");
    static final CompoundName CURRENCY = new CompoundName("currency");

    @Override
    public Result search(Query query, Execution execution) {
        String minStr = query.properties().getString(MIN_PRICE);
        String maxStr = query.properties().getString(MAX_PRICE);
        String currencyStr = query.properties().getString(CURRENCY);
        if (minStr == null || maxStr == null || currencyStr == null) {
            return execution.search(query);
        }
        float minPrice, maxPrice;
        try {
            minPrice = Float.parseFloat(minStr);
            maxPrice = Float.parseFloat(maxStr);
        } catch (NumberFormatException nfe) {
            return execution.search(query);
        }
        if (maxPrice < minPrice) {
            return execution.search(query);
        }
        // we have currencyStr
        // we need to get the currency document that has the same code=currencyStr
        // when we obtain that currency document we can store that document's factor map as a map of string to double
        // we also need to have a codeToIdx map, that is because the or clauses need to contain the idx
        // or (currency_idx = idx and price >= convertedMinPrice and price <= convertedMaxPrice)
        // if we do not have a codeToIdx map then maybe we can say:
        // or (code = code and price >= convertedMinPrice and price <= convertedMaxPrice)
        // if we do not look at the currency docs to retrieve this than all this information need to be passed along
        // with this searchChain using this custom searcher
        // best approach is to probably cache the currency information and use that so we never have to wait for it
        // for the first iteration we just hardcode this:
        String sourceCurrency = currencyStr.toUpperCase(Locale.ROOT);
        // 1) Fetch fromRates = factor_map of the source currency doc (rates FROM source -> targets)
        Map<String, Double> fromRates = fetchFromRates(sourceCurrency);
        if (fromRates.isEmpty()) {
            return execution.search(query);
        }
        // 2) Fetch codeToIdx for all currencies (code -> idx)
        Map<String, Integer> codeToIdx = fetchCodeToIdx();
        if (codeToIdx.isEmpty()) {
            return execution.search(query);
        }
        // 3) Build OR of (currency_idx == idx(target) AND price in [min*rate, max*rate])
        OrItem anyCurrencyClause = new OrItem();
        for (Map.Entry<String, Double> e : fromRates.entrySet()) {
            String target = e.getKey().toUpperCase(Locale.ROOT);
            Double rate = e.getValue();
            Integer idx = codeToIdx.get(target);
            if (rate == null || idx == null) continue;
            double convertedMin = minPrice * rate;
            double convertedMax = maxPrice * rate;
            AndItem perCurrency = new AndItem();
            perCurrency.addItem(new RangeItem(idx, idx, "currency_idx"));
            perCurrency.addItem(new RangeItem(
                    (long) Math.floor(convertedMin),
                    (long) Math.ceil(convertedMax),
                    "price"
            ));
            anyCurrencyClause.addItem(perCurrency);
        }

        // 4) AND our filter with the user’s existing root
        if (anyCurrencyClause.getItemCount() == 0) {
            return execution.search(query);
        }
        Item root = query.getModel().getQueryTree().getRoot();
        if (root == null) {
            query.getModel().getQueryTree().setRoot(anyCurrencyClause);
        } else {
            AndItem combined = new AndItem();
            combined.addItem(root);
            combined.addItem(anyCurrencyClause);
            query.getModel().getQueryTree().setRoot(combined);
        }
        return execution.search(query);
    }

    // ---- hardcoded stubs: tiny for first-iteration testing ----
    private Map<String, Double> fetchFromRates(String sourceCurrency) {
        Map<String, Double> m = new HashMap<>();
        switch (sourceCurrency) {
            case "USD":
                m.put("EUR", 0.89879561);
                m.put("NOK", 10.61571125);
                m.put("USD", 1.0);
                break;
            case "EUR":
                m.put("USD", 1.21521449);
                m.put("NOK", 12.33045623);
                m.put("EUR", 1.0);
                break;
            case "NOK":
                m.put("USD", 0.10324712);
                m.put("EUR", 0.08890074);
                m.put("NOK", 1.0);
                break;
            default:
        }
        return m;
    }

    private Map<String, Integer> fetchCodeToIdx() {
        Map<String, Integer> m = new HashMap<>();
        m.put("USD", 27);
        m.put("EUR", 7);
        m.put("NOK", 18);
        return m;
    }

}
