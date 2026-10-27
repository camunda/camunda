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

public class CamundaSearchRequestBuilderTest {

  @Test
  public void shouldBuildSearchRequest() {
    // given + when
    final var searchRequest = RequestBuilders.searchRequest().index("foo").build();

    // then
    assertThat(searchRequest.index()).hasSize(1).contains("foo");
  }
}
