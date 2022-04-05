/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.util;

import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.Cardinality;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.camunda.operate.util.ThreadUtil.sleepFor;

public abstract class ElasticsearchUtil {

  public static int getFieldCardinality(RestHighLevelClient esClient, String aliasName, String fieldName) throws IOException {
    return getFieldCardinalityWithRequest(esClient, aliasName, fieldName, null);
  }

  public static int getFieldCardinalityWithRequest(RestHighLevelClient esClient, String aliasName, String fieldName, QueryBuilder query) throws IOException {
    final String aggName = "agg";
    AggregationBuilder agg = AggregationBuilders.cardinality(aggName)
      .field(fieldName)
      .precisionThreshold(40000);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().aggregation(agg);
    if (query != null) {
      searchSourceBuilder.query(query);
    }
    SearchRequest searchRequest = new SearchRequest(aliasName)
      .source(searchSourceBuilder);
    final long value = ((Cardinality) esClient.search(searchRequest, RequestOptions.DEFAULT).getAggregations().get(aggName)).getValue();
    return (int)value;
  }

  public static void flushData(RestHighLevelClient esClient) {
    try {
      final FlushRequest flushRequest = new FlushRequest();
      flushRequest.waitIfOngoing(true);
      flushRequest.force(true);
      esClient.indices().flush(flushRequest, RequestOptions.DEFAULT);
      sleepFor(500);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public static int getDocCount(RestHighLevelClient esClient, String aliasName) throws IOException {
    SearchRequest searchRequest = new SearchRequest(aliasName)
      .source(new SearchSourceBuilder());
    return (int)esClient.search(searchRequest, RequestOptions.DEFAULT).getHits().getTotalHits().value;
  }

  public static List<String> getProcessIds(RestHighLevelClient esClient, String indexAlias, int size) {
    try {
      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .fetchSource(false)
              .from(0)
              .size(size);
      SearchRequest searchRequest =
          new SearchRequest(indexAlias).source(searchSourceBuilder);
      return requestIdsFor(esClient, searchRequest);
    } catch (IOException ex) {
      throw new RuntimeException("Error occurred when reading processIds from Elasticsearch", ex);
    }
  }

  private static List<String> requestIdsFor(RestHighLevelClient esClient, SearchRequest searchRequest) throws IOException{
    final SearchHits hits = esClient.search(searchRequest, RequestOptions.DEFAULT).getHits();
    return Arrays.stream(hits.getHits()).collect(ArrayList::new, (list, hit) -> list.add(hit.getId()), (list1, list2) -> list1.addAll(list2));
  }

}
