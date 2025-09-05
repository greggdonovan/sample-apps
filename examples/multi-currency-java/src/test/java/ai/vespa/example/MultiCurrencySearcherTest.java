// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.example;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.RangeItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.yql.MinimalQueryInserter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static java.net.URLEncoder.encode;
import static org.junit.jupiter.api.Assertions.*;


public class MultiCurrencySearcherTest {

    @Test
    public void test_query_build_with_range_and_currency() {
        Chain<Searcher> chain = new Chain<>(new MinimalQueryInserter(), new MultiCurrencySearcher());
        Execution execution = new Execution(chain, Execution.Context.createContextStub());

        String yql = encode("select * from sources item where true", StandardCharsets.UTF_8);
        Query q = new Query("/search/?yql=" + yql + "&min-price=20&max-price=80&currency=usd");

        Result r = execution.search(q);
        assertNull(r.hits().getErrorHit(), "Should not produce error hit");

        Item root = r.getQuery().getModel().getQueryTree().getRoot();
        assertNotNull(root, "Query root should be present");
        assertInstanceOf(AndItem.class, root, "Root should be an AND combining original root and our filter");

        AndItem rootAnd = (AndItem)root;
        OrItem currencyOr = (OrItem) rootAnd.items().stream().filter(child -> child instanceof OrItem).findFirst().orElse(null);
        assertNotNull(currencyOr, "Expected an OR with per-currency clauses");
        assertTrue(currencyOr.getItemCount() >= 1, "Expected at least one per-currency clause");

        currencyOr.items().forEach(clause -> {
            assertInstanceOf(AndItem.class, clause, "Each OR child should be an AND clause");
            AndItem and = (AndItem) clause;

            boolean hasCurrencyIdx = and.items().stream()
                    .filter(RangeItem.class::isInstance)
                    .map(RangeItem.class::cast)
                    .anyMatch(rItem ->
                            "currency_idx".equals(rItem.getIndexName()) &&
                                    Objects.equals(rItem.getFrom(), rItem.getTo()));

            boolean hasPrice = and.items().stream()
                    .filter(RangeItem.class::isInstance)
                    .map(RangeItem.class::cast)
                    .anyMatch(rItem ->
                            "price".equals(rItem.getIndexName()) &&
                                    rItem.getFrom().longValue() <= rItem.getTo().longValue());

            assertTrue(hasCurrencyIdx, "Per-currency AND must include exact currency_idx range");
            assertTrue(hasPrice, "Per-currency AND must include a price range");
        });
    }
}
