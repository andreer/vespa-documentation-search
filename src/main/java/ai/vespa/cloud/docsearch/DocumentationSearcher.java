// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud.docsearch;

import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

public class DocumentationSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        String userQuery = query.properties().getString("term");
        if (userQuery == null) return execution.search(query);

        Result suggestionResult = getSuggestions(userQuery, execution);

        Query docQuery = new Query();
        docQuery.getModel().setRestrict("doc");
        WeakAndItem weakAndItem = new WeakAndItem();
        if (suggestionResult.getHitCount() > 0) {
            docQuery.setHits(20);
            for (String term: suggestedTerms(suggestionResult))
                weakAndItem.addItem(new WordItem(term, true));
        }
        else {
            docQuery.setHits(10);
            for (String term: userQuery.split(" "))
                weakAndItem.addItem(new WordItem(term, true));
        }
        docQuery.getModel().getQueryTree().setRoot(weakAndItem);
        docQuery.getRanking().setProfile("documentation");
        Result documentResult = execution.search(docQuery);
        return combineHits(documentResult, suggestionResult);
    }

    private Result getSuggestions(String userQuery, Execution execution) {
        Query query = new Query();
        query.getModel().setRestrict("term");
        query.getModel().getQueryTree().setRoot(new PrefixItem(userQuery, "default"));
        query.getRanking().setProfile("term_rank");
        query.setHits(10);
        Result suggestionResult = execution.search(query);
        execution.fill(suggestionResult);
        return suggestionResult;
    }

    private String[] suggestedTerms(Result suggestionResult) {
        Hit topHit = suggestionResult.hits().get(0);
        if (topHit.fields().get("term") == null)
            throw new RuntimeException("Suggestion result unexpectedly missing 'term' field");
        return topHit.getField("term").toString().split(" ");
    }

    private Result combineHits(Result result1, Result result2) {
        for (Hit hit: result2.hits())
            result1.hits().add(hit);
        return result1;
    }

}
