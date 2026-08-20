/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchExistsQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.entities.ProcessDefinitionEntity.ProcessDefinitionState;
import io.camunda.search.filter.FilterBuilders;
import org.junit.jupiter.api.Test;

class ProcessDefinitionFilterTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldQueryByProcessDefinitionKey() {
    final var filter = FilterBuilders.processDefinition(f -> f.processDefinitionKeys(100L));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("key");
              assertThat(t.value().longValue()).isEqualTo(100L);
            });
  }

  @Test
  void shouldQueryByStateDeletedAsExactMatch() {
    final var filter =
        FilterBuilders.processDefinition(f -> f.state(ProcessDefinitionState.DELETED));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("state");
              assertThat(t.value().stringValue()).isEqualTo("DELETED");
            });
  }

  @Test
  void shouldQueryByStateActiveAsValueOrMissingField() {
    final var filter =
        FilterBuilders.processDefinition(f -> f.state(ProcessDefinitionState.ACTIVE));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            bool -> {
              assertThat(bool.should()).hasSize(2);
              assertThat(bool.should().getFirst().queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      t -> {
                        assertThat(t.field()).isEqualTo("state");
                        assertThat(t.value().stringValue()).isEqualTo("ACTIVE");
                      });
              assertThat(bool.should().getLast().queryOption())
                  .isInstanceOfSatisfying(
                      SearchBoolQuery.class,
                      nested -> {
                        assertThat(nested.mustNot()).hasSize(1);
                        assertThat(nested.mustNot().getFirst().queryOption())
                            .isInstanceOfSatisfying(
                                SearchExistsQuery.class,
                                exists -> assertThat(exists.field()).isEqualTo("state"));
                      });
            });
  }

  @Test
  void shouldNotFilterByStateWhenNotSet() {
    final var filter = FilterBuilders.processDefinition(f -> f.processDefinitionKeys(100L));

    final var searchRequest = transformQuery(filter);

    // only the key filter is present — state contributes nothing when unset
    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(SearchTermQuery.class, t -> assertThat(t.field()).isEqualTo("key"));
  }
}
