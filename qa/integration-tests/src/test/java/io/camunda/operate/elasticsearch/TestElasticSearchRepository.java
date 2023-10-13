/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

@Component
@Conditional(ElasticsearchCondition.class)
public class TestElasticSearchRepository implements TestSearchRepository {
  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public <R> List<R> searchAll(String index, Class<R> clazz) throws IOException {
    final SearchRequest searchRequest = new SearchRequest(index)
      .source(new SearchSourceBuilder()
        .query(matchAllQuery()));
    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    return ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, clazz);
  }
}
