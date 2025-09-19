/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.searchrepository;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpensearchCondition.class)
public class TestZeebeOpensearchRepository implements TestZeebeRepository {
  @Autowired protected RichOpenSearchClient richOpenSearchClient;

  @Override
  public <R> List<R> scrollTerm(
      final String index, final String field, final long value, final Class<R> clazz) {
    final var requestBuilder = searchRequestBuilder(index).query(term(field, value));

    return richOpenSearchClient.doc().scrollValues(requestBuilder, clazz);
  }
}
