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
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import org.junit.jupiter.api.Test;

class ProcessInstanceIndexMappingTest {

  @Test
  void shouldMapAgentInstancesAsNested() {
    // given
    final ProcessInstanceIndexES index = new ProcessInstanceIndexES("test");

    // when
    final TypeMapping mapping = index.addProperties(new TypeMapping.Builder()).build();

    // then
    final Property agentInstancesProperty =
        mapping.properties().get(ProcessInstanceIndex.AGENT_INSTANCES);
    assertThat(agentInstancesProperty).isNotNull();
    assertThat(agentInstancesProperty._kind())
        .as("agentInstances must be nested, not object — wrong type silently breaks aggregations")
        .isEqualTo(Property.Kind.Nested);
  }

  @Test
  void shouldHaveVersionNine() {
    assertThat(ProcessInstanceIndex.VERSION).isEqualTo(9);
  }
}
