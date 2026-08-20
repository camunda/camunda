/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.it.extension.db;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import java.util.HashMap;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;

public class TermsQueryContainer {
  HashMap<String, List<String>> termQueries;

  public TermsQueryContainer() {
    termQueries = new HashMap<>();
  }

  public void addTermQuery(final String term, final List<String> values) {
    termQueries.put(term, values);
  }

  public void addTermQuery(final String term, final String value) {
    termQueries.put(term, List.of(value));
  }

  Query toElasticSearchQuery() {
    return Query.of(
        q ->
            q.bool(
                b -> {
                  for (final String term : termQueries.keySet()) {
                    b.must(
                        m ->
                            m.terms(
                                t ->
                                    t.field(term)
                                        .terms(
                                            tt ->
                                                tt.value(
                                                    termQueries.get(term).stream()
                                                        .map(FieldValue::of)
                                                        .toList()))));
                  }
                  return b;
                }));
  }

  BoolQuery toOpenSearchQuery() {
    final BoolQuery.Builder query = new BoolQuery.Builder();
    for (final String term : termQueries.keySet()) {
      query.must(QueryDSL.stringTerms(term, termQueries.get(term)));
    }
    return query.build();
  }
}
