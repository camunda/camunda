/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.result;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.query.SearchQueryBuilders;
import org.junit.jupiter.api.Test;

class DecisionInstanceResultConfigTest extends AbstractResultConfigTest {

  @Test
  void shouldSourceConfigIncludeEvaluatedInputs() {
    // when
    final var source =
        transformRequest(
            SearchQueryBuilders.decisionInstanceSearchQuery(
                q -> q.resultConfig(r -> r.evaluatedInputs().include())));

    // then
    assertThat(source.sourceFilter().includes()).containsExactly("evaluatedInputs");
    assertThat(source.sourceFilter().excludes()).isNull();
  }

  @Test
  void shouldSourceConfigIncludeEvaluatedOutputs() {
    // when
    final var source =
        transformRequest(
            SearchQueryBuilders.decisionInstanceSearchQuery(
                q -> q.resultConfig(r -> r.evaluatedOutputs().exclude())));

    // then
    assertThat(source.sourceFilter().excludes()).containsExactly("evaluatedOutputs");
    assertThat(source.sourceFilter().includes()).isNull();
  }

  @Test
  void shouldSourceConfigExcludeEvaluatedInputsAndEvaluatedOutputs() {
    // when
    final var source =
        transformRequest(
            SearchQueryBuilders.decisionInstanceSearchQuery(
                q ->
                    q.resultConfig(
                        r -> r.evaluatedInputs().exclude().evaluatedOutputs().exclude())));

    // then
    assertThat(source.sourceFilter().excludes())
        .containsExactly("evaluatedInputs", "evaluatedOutputs");
    assertThat(source.sourceFilter().includes()).isNull();
  }
}
