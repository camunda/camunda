/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessInstanceEntityTransformerTest {

  private final ProcessInstanceEntityTransformer transformer =
      new ProcessInstanceEntityTransformer();

  @Mock private ProcessInstanceForListViewEntity entityValue;

  @BeforeEach
  void setUp() {
    when(entityValue.getKey()).thenReturn(123L);
    when(entityValue.getBpmnProcessId()).thenReturn("demoProcess");
    when(entityValue.getProcessName()).thenReturn("Demo Process");
    when(entityValue.getProcessVersion()).thenReturn(1);
    when(entityValue.getProcessDefinitionKey()).thenReturn(456L);
    when(entityValue.getStartDate()).thenReturn(OffsetDateTime.now());
    when(entityValue.getState())
        .thenReturn(io.camunda.webapps.schema.entities.listview.ProcessInstanceState.ACTIVE);
    when(entityValue.isIncident()).thenReturn(false);
    when(entityValue.getTenantId()).thenReturn("tenant");
    when(entityValue.getTags()).thenReturn(Set.of());
  }

  @Test
  void shouldMapSuspendedDate() {
    // given
    final var suspendedDate = OffsetDateTime.parse("2026-07-16T00:00:00Z");
    when(entityValue.getSuspendedDate()).thenReturn(suspendedDate);

    // when
    final var transformed = transformer.apply(entityValue);

    // then
    assertThat(transformed.suspendedDate()).isEqualTo(suspendedDate);
  }

  @Test
  void shouldMapNullSuspendedDate() {
    // given
    when(entityValue.getSuspendedDate()).thenReturn(null);

    // when
    final var transformed = transformer.apply(entityValue);

    // then
    assertThat(transformed.suspendedDate()).isNull();
  }

  @Test
  void shouldMapSuspendedState() {
    // given
    when(entityValue.getState())
        .thenReturn(io.camunda.webapps.schema.entities.listview.ProcessInstanceState.SUSPENDED);

    // when
    final var transformed = transformer.apply(entityValue);

    // then
    assertThat(transformed.state()).isEqualTo(ProcessInstanceState.SUSPENDED);
  }
}
