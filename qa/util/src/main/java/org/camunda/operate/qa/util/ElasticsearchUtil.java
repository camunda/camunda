/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.util;

import java.io.IOException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;

public abstract class ElasticsearchUtil {

  public static int getFieldCardinality(RestHighLevelClient esClient, String aliasName, String fieldName) throws IOException {
    final String aggName = "agg";
    AggregationBuilder agg = AggregationBuilders.cardinality(aggName)
      .field(fieldName)
      .precisionThreshold(40000);
    SearchRequest searchRequest = new SearchRequest(aliasName)
      .source(new SearchSourceBuilder()
        .aggregation(agg));
    final long value = ((Cardinality) esClient.search(searchRequest, RequestOptions.DEFAULT).getAggregations().get(aggName)).getValue();
    return (int)value;
  }

  public static int getDocCount(RestHighLevelClient esClient, String aliasName) throws IOException {
    SearchRequest searchRequest = new SearchRequest(aliasName)
      .source(new SearchSourceBuilder());
    return (int)esClient.search(searchRequest, RequestOptions.DEFAULT).getHits().getTotalHits();
  }
}
