/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.AgentRunFilterDataDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRunQueryFilterESTest {

  private final AgentRunQueryFilterES underTest = new AgentRunQueryFilterES();

  @Test
  void shouldRestrictRunsToSelectedAgentsAndModels() {
    // given
    final BoolQuery.Builder query = new BoolQuery.Builder();

    // when
    underTest.addFilters(
        query,
        List.of(new AgentRunFilterDataDto(List.of("loan:review"), List.of("gpt-4o"), List.of())),
        null);

    // then
    final List<Query> filters = query.build().filter();
    assertThat(filters).hasSize(2);
    assertThat(filters.get(0).terms().field()).isEqualTo("agentId");
    assertThat(filters.get(0).terms().terms().value())
        .extracting(FieldValue::stringValue)
        .containsExactly("loan:review");
    assertThat(filters.get(1).terms().field()).isEqualTo("model");
  }

  @Test
  void shouldNotRestrictRunsWhenNothingIsSelected() {
    // given
    final BoolQuery.Builder query = new BoolQuery.Builder();

    // when
    underTest.addFilters(query, List.of(new AgentRunFilterDataDto()), null);

    // then
    assertThat(query.build().filter()).isEmpty();
  }
}
