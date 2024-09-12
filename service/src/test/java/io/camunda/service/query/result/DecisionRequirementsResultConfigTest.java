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
import io.camunda.service.DecisionRequirementsServices;
import io.camunda.service.query.filter.DecisionRequirementsSearchQueryStub;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.util.StubbedCamundaSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DecisionRequirementsResultConfigTest {
  private DecisionRequirementsServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new DecisionRequirementsSearchQueryStub().registerWith(client);
    services = new DecisionRequirementsServices(null, null, null);
  }

  @Test
  public void shouldSourceConfigIncludeXml() {
    // when
    services.search(
        SearchQueryBuilders.decisionRequirementsSearchQuery(
            q -> q.resultConfig(r -> r.xml().include())));

    // then
    final SearchQueryRequest searchRequest = client.getSingleSearchRequest();

    final var source = searchRequest.source();
    assertThat(source.sourceFilter().includes()).containsExactly("xml");
    assertThat(source.sourceFilter().excludes()).isEmpty();
  }

  @Test
  public void shouldSourceConfigExcludeXml() {
    // when
    services.search(
        SearchQueryBuilders.decisionRequirementsSearchQuery(
            q -> q.resultConfig(r -> r.xml().exclude())));

    // then
    final SearchQueryRequest searchRequest = client.getSingleSearchRequest();

    final var source = searchRequest.source();
    assertThat(source.sourceFilter().excludes()).containsExactly("xml");
    assertThat(source.sourceFilter().includes()).isEmpty();
  }
}
