/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.AdvancedStringFilter;
import io.camunda.gateway.protocol.model.simple.ProcessDefinitionFilter;
import io.camunda.gateway.protocol.model.simple.ProcessDefinitionOrFilterFields;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimpleSearchQueryMapperTest {

  @Test
  void shouldMapProcessDefinitionOrGroups() {
    // given
    final var simpleFilter =
        new ProcessDefinitionFilter()
            .isLatestVersion(true)
            .$or(
                List.of(
                    new ProcessDefinitionOrFilterFields().processDefinitionId("invoice"),
                    new ProcessDefinitionOrFilterFields().name("Invoice")));

    // when
    final var filter = SimpleSearchQueryMapper.toProcessDefinitionFilter(simpleFilter);

    // then
    assertThat(filter.getIsLatestVersion()).isTrue();
    assertThat(filter.get$Or()).hasSize(2);
    assertThat(((AdvancedStringFilter) filter.get$Or().get(0).getProcessDefinitionId()).get$Eq())
        .isEqualTo("invoice");
    assertThat(filter.get$Or().get(0).getName()).isNull();
    assertThat(((AdvancedStringFilter) filter.get$Or().get(1).getName()).get$Eq())
        .isEqualTo("Invoice");
    assertThat(filter.get$Or().get(1).getProcessDefinitionId()).isNull();
  }

  @Test
  void shouldNotSetProcessDefinitionOrGroupsWhenListIsEmpty() {
    // given
    final var simpleFilter = new ProcessDefinitionFilter().$or(List.of());

    // when
    final var filter = SimpleSearchQueryMapper.toProcessDefinitionFilter(simpleFilter);

    // then
    assertThat(filter.get$Or()).isNull();
  }
}
