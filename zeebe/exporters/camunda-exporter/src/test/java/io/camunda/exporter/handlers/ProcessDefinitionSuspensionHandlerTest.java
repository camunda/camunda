/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessDefinitionState;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessDefinitionSuspensionResolvedVersionValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionResolvedVersionValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProcessDefinitionSuspensionHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ProcessDefinitionSuspensionHandler underTest =
      new ProcessDefinitionSuspensionHandler("test-process");

  @Test
  void shouldGenerateIdsOnlyForVersionsWhoseStateChanges() {
    // given
    final Record<ProcessDefinitionSuspensionRecordValue> record =
        suspensionRecord(
            ProcessDefinitionSuspensionIntent.SUSPENDED,
            List.of(
                version(11L, ProcessDefinitionSuspensionOutcome.SUSPENDED),
                version(12L, ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED),
                version(13L, ProcessDefinitionSuspensionOutcome.DRAINING)));

    // when
    final List<String> ids = underTest.generateIds(record);

    // then
    assertThat(ids).containsExactly("11");
  }

  @ParameterizedTest
  @MethodSource("targetStates")
  void shouldSetStateFromIntent(
      final ProcessDefinitionSuspensionIntent intent,
      final ProcessDefinitionSuspensionOutcome outcome,
      final ProcessDefinitionState expectedState) {
    // given
    final Record<ProcessDefinitionSuspensionRecordValue> record =
        suspensionRecord(intent, List.of(version(11L, outcome)));
    final ProcessEntity entity = new ProcessEntity().setId("11");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getState()).isEqualTo(expectedState);
  }

  @Test
  void shouldFlushAnyEntityState() throws Exception {
    // given - entity has a state other than SUSPENDED
    final ProcessEntity entity = new ProcessEntity().setId("11");
    entity.setState(ProcessDefinitionState.DRAINING);
    final TargetIndex index = TargetIndex.mainIndex("test-process");
    final BatchRequest batchRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, entity, batchRequest);

    // then - the set state gets flushed, not SUSPENDED
    verify(batchRequest)
        .update(index, "11", Map.of(ProcessIndex.STATE, ProcessDefinitionState.DRAINING));
  }

  private static Stream<Arguments> targetStates() {
    return Stream.of(
        Arguments.of(
            ProcessDefinitionSuspensionIntent.SUSPENDED,
            ProcessDefinitionSuspensionOutcome.SUSPENDED,
            ProcessDefinitionState.SUSPENDED),
        Arguments.of(
            ProcessDefinitionSuspensionIntent.RESUMED,
            ProcessDefinitionSuspensionOutcome.RESUMED,
            ProcessDefinitionState.ACTIVE));
  }

  private Record<ProcessDefinitionSuspensionRecordValue> suspensionRecord(
      final ProcessDefinitionSuspensionIntent intent,
      final List<ProcessDefinitionSuspensionResolvedVersionValue> versions) {
    final ProcessDefinitionSuspensionRecordValue value =
        ImmutableProcessDefinitionSuspensionRecordValue.builder()
            .from(factory.generateObject(ImmutableProcessDefinitionSuspensionRecordValue.class))
            .withResolvedVersions(versions)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_DEFINITION_SUSPENSION, r -> r.withIntent(intent).withValue(value));
  }

  private ProcessDefinitionSuspensionResolvedVersionValue version(
      final long processDefinitionKey, final ProcessDefinitionSuspensionOutcome outcome) {
    return ImmutableProcessDefinitionSuspensionResolvedVersionValue.builder()
        .withProcessDefinitionKey(processDefinitionKey)
        .withVersion(1)
        .withOutcome(outcome)
        .build();
  }
}
