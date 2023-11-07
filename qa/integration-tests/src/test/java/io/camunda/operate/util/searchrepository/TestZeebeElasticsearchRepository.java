/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.searchrepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.camunda.operate.util.ElasticsearchUtil.scroll;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
@Conditional(ElasticsearchCondition.class)
public class TestZeebeElasticsearchRepository implements TestZeebeRepository {
  @Autowired
  @Qualifier("zeebeEsClient")
  protected RestHighLevelClient zeebeEsClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public <R> List<R> scrollTerm(String index, String field, long value, Class<R> clazz) {
    List<R> result = new ArrayList<>();

    SearchRequest request = new SearchRequest(index).source(
      new SearchSourceBuilder().query(termQuery(field, value)));

    Function<SearchHits, List<R>> hitsToListR = hits -> Arrays.stream(hits.getHits())
      .map(SearchHit::getSourceAsString)
      .map(source -> ElasticsearchUtil.fromSearchHit(source, objectMapper, clazz)).toList();

    Consumer<SearchHits> hitsConsumer = hits -> result.addAll(hitsToListR.apply(hits));

    try {
      scroll(request, hitsConsumer, zeebeEsClient);
      return result;
    } catch (IOException e) {
      throw new OperateRuntimeException(e);
    }
  }
}
