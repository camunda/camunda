/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class CamundaSearchResponseBuilderTest {

  @Test
  public void shouldBuildSearchRequest() {
    // given + when
    final var searchResponse =
        new SearchQueryResponse.Builder<TestEntity>().totalHits(100L).build();

    // then
    assertThat(searchResponse.totalHits()).isEqualTo(100L);
  }

  private static record TestEntity() {}
}
