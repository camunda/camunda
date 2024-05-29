/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.mappers.DataStoreTransformer;
import org.junit.jupiter.api.Test;

public class ElasticsearchTransfomersTest {

  @Test
  public void should() {
    // given
    final DataStoreSearchRequest request =
        DataStoreSearchRequest.of(b -> b.index("operate-list-view-8.3.0_").size(1));

    final ElasticsearchTransformers elasticsearchTransformers = new ElasticsearchTransformers();

    // when
    final DataStoreTransformer<DataStoreSearchRequest, SearchRequest> transformer =
        elasticsearchTransformers.getTransformer(DataStoreSearchRequest.class);
    final SearchRequest actual = transformer.apply(request);

    // then
    assertThat(actual.index()).hasSize(1).contains("operate-list-view-8.3.0_");
  }
}
