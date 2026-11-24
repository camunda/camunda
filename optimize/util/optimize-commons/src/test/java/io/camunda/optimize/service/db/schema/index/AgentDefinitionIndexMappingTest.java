/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.es.schema.index.AgentDefinitionIndexES;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AgentDefinitionIndexMappingTest {

  @Test
  void shouldMapEveryFieldOfTheAgentDefinitionSchema() {
    // given
    final TypeMapping mapping =
        new AgentDefinitionIndexES().addProperties(new TypeMapping.Builder()).build();

    // when
    final Map<String, Property.Kind> kinds =
        mapping.properties().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()._kind()));

    // then the mapping holds exactly the fields of data model proposal 01, section 1.2
    assertThat(kinds)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "agentId", Property.Kind.Keyword,
                "tenantId", Property.Kind.Keyword,
                "processDefinitionKey", Property.Kind.Keyword,
                "flowNodeId", Property.Kind.Keyword,
                "name", Property.Kind.Keyword,
                "agentType", Property.Kind.Keyword,
                "processDefinitionVersions", Property.Kind.Keyword));
  }

  @Test
  void shouldStartAtVersionOne() {
    assertThat(new AgentDefinitionIndexES().getVersion()).isEqualTo(1);
  }
}
