/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.it.extension.db;

import java.util.HashMap;
import java.util.List;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;

public class TermsQueryContainer {
  HashMap<String, List<String>> termQueries;

  public TermsQueryContainer() {
    termQueries = new HashMap<>();
  }

  public void addTermQuery(String term, List<String> values) {
    termQueries.put(term, values);
  }

  public void addTermQuery(String term, String value) {
    termQueries.put(term, List.of(value));
  }

  BoolQueryBuilder toElasticSearchQuery() {
    BoolQueryBuilder query = new BoolQueryBuilder();
    for (String term : termQueries.keySet()) {
      query.must(QueryBuilders.termsQuery(term, termQueries.get(term)));
    }
    return query;
  }

  BoolQuery toOpenSearchQuery() {
    BoolQuery.Builder query = new BoolQuery.Builder();
    for (String term : termQueries.keySet()) {
      query.must(QueryDSL.stringTerms(term, termQueries.get(term)));
    }
    return query.build();
  }
}
