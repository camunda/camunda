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

public class DecisionRequirementsResultConfigTest extends AbstractResultConfigTest {

  @Test
  public void shouldSourceConfigIncludeXml() {
    // when
    final var source =
        transformRequest(
            SearchQueryBuilders.decisionRequirementsSearchQuery(
                q -> q.resultConfig(r -> r.xml().include())));

    // then
    assertThat(source.sourceFilter().includes()).containsExactly("xml");
    assertThat(source.sourceFilter().excludes()).isNull();
  }

  @Test
  public void shouldSourceConfigExcludeXml() {
    // when
    final var source =
        transformRequest(
            SearchQueryBuilders.decisionRequirementsSearchQuery(
                q -> q.resultConfig(r -> r.xml().exclude())));

    // then
    assertThat(source.sourceFilter().excludes()).containsExactly("xml");
    assertThat(source.sourceFilter().includes()).isNull();
  }
}
