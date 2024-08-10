/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.searchrepository;

import static io.camunda.operate.util.ElasticsearchUtil.scroll;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticsearchCondition.class)
public class TestZeebeElasticsearchRepository implements TestZeebeRepository {
  @Autowired
  @Qualifier("zeebeEsClient")
  protected RestHighLevelClient zeebeEsClient;

  @Autowired private ObjectMapper objectMapper;

  @Override
  public <R> List<R> scrollTerm(String index, String field, long value, Class<R> clazz) {
    final List<R> result = new ArrayList<>();

    final SearchRequest request =
        new SearchRequest(index).source(new SearchSourceBuilder().query(termQuery(field, value)));

    final Function<SearchHits, List<R>> hitsToListR =
        hits ->
            Arrays.stream(hits.getHits())
                .map(SearchHit::getSourceAsString)
                .map(source -> ElasticsearchUtil.fromSearchHit(source, objectMapper, clazz))
                .toList();

    final Consumer<SearchHits> hitsConsumer = hits -> result.addAll(hitsToListR.apply(hits));

    try {
      scroll(request, hitsConsumer, zeebeEsClient);
      return result;
    } catch (IOException e) {
      throw new OperateRuntimeException(e);
    }
  }
}
