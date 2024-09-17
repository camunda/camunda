/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.result;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.query.filter.ProcessInstanceSearchQueryStub;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.util.StubbedCamundaSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProcessInstanceResultConfigTest {
  private ProcessInstanceServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new ProcessInstanceSearchQueryStub().registerWith(client);
    services = new ProcessInstanceServices(null, client);
  }

  @Test
  public void shouldSourceConfigIncludeProcessKey() {
    // when
    services.search(
        SearchQueryBuilders.processInstanceSearchQuery(
            q -> q.resultConfig(r -> r.processInstanceKey().include())));

    // then
    final SearchQueryRequest searchRequest = client.getSingleSearchRequest();

    final var source = searchRequest.source();
    assertThat(source.sourceFilter().includes()).containsExactly("key");
    assertThat(source.sourceFilter().excludes()).isEmpty();
  }

  @Test
  public void shouldSourceConfigExcludeProcessKey() {
    // when
    services.search(
        SearchQueryBuilders.processInstanceSearchQuery(
            q -> q.resultConfig(r -> r.processInstanceKey().exclude())));

    // then
    final SearchQueryRequest searchRequest = client.getSingleSearchRequest();

    final var source = searchRequest.source();
    assertThat(source.sourceFilter().excludes()).containsExactly("key");
    assertThat(source.sourceFilter().includes()).isEmpty();
  }
}
