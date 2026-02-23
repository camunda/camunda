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

import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobEntityTransformerTest {

  private final JobEntityTransformer transformer = new JobEntityTransformer();

  @Mock private io.camunda.webapps.schema.entities.JobEntity entityValue;

  @BeforeEach
  void setUp() {
    when(entityValue.getKey()).thenReturn(123456L);
    when(entityValue.getState()).thenReturn(JobState.CREATED.name());
    when(entityValue.getJobKind()).thenReturn(JobKind.BPMN_ELEMENT.name());
    when(entityValue.getListenerEventType()).thenReturn(ListenerEventType.UNSPECIFIED.name());
  }

  @Test
  void shouldMapRootProcessInstanceKey() {
    when(entityValue.getRootProcessInstanceKey()).thenReturn(999L);

    final var transformed = transformer.apply(entityValue);

    assertThat(transformed.rootProcessInstanceKey()).isEqualTo(999L);
  }

  @Test
  void shouldMapNullRootProcessInstanceKey() {
    when(entityValue.getRootProcessInstanceKey()).thenReturn(null);

    final var transformed = transformer.apply(entityValue);

    assertThat(transformed.rootProcessInstanceKey()).isNull();
  }

  @ParameterizedTest
  @EnumSource(ListenerEventType.class)
  void handlesAllListenerEventTypes(final ListenerEventType listenerEventType) {
    when(entityValue.getListenerEventType()).thenReturn(listenerEventType.name());

    final var transformed = transformer.apply(entityValue);
    assertThat(transformed.listenerEventType()).isEqualTo(listenerEventType);
  }

  @ParameterizedTest
  @EnumSource(JobKind.class)
  void handlesAllJobKinds(final JobKind jobKind) {
    when(entityValue.getJobKind()).thenReturn(jobKind.name());

    final var transformed = transformer.apply(entityValue);
    assertThat(transformed.kind()).isEqualTo(jobKind);
  }

  @ParameterizedTest
  @EnumSource(JobState.class)
  void handlesAllJobStates(final JobState jobState) {
    when(entityValue.getState()).thenReturn(jobState.name());

    final var transformed = transformer.apply(entityValue);
    assertThat(transformed.state()).isEqualTo(jobState);
  }
}
