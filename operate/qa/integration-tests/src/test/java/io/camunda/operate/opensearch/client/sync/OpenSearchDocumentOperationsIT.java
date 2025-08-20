/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.opensearch.client.sync;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sourceInclude;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.opensearch.client.AbstractOpenSearchOperationIT;
import io.camunda.webapps.schema.descriptors.index.UserIndex;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class OpenSearchDocumentOperationsIT extends AbstractOpenSearchOperationIT {
  @Autowired private UserIndex userIndex;

  @Test
  public void searchUniqueShouldDeserializeLocalRecord() {
    // given
    final String id = "id";
    opensearchTestDataHelper.addUser(id, "displayName", "password");

    // when
    record Result(String name) {}
    final var searchRequestBuilder =
        searchRequestBuilder(userIndex.getFullQualifiedName())
            .query(term("id", id))
            .source(sourceInclude("name"));

    final Result result =
        richOpenSearchClient.doc().searchUnique(searchRequestBuilder, Result.class, id);

    // then
    assertThat(result.name()).isEqualTo("displayName");
  }
}
