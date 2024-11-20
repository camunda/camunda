/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.stream.FakeProcessingResultBuilder;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SuppressWarnings({"unchecked", "unused"})
@ExtendWith(ProcessingStateExtension.class)
final class DeploymentReconstructProcessorTest {
  private ZeebeDb<?> zeebeDb;
  private TransactionContext transactionContext;
  private MutableProcessingState state;
  private DeploymentReconstructProcessor processor;
  private FakeProcessingResultBuilder<UnifiedRecordValue> resultBuilder;

  @BeforeEach
  void setUp() {
    final var keyGenerator = new DbKeyGenerator(1, zeebeDb, transactionContext);
    final var eventAppliers = new EventAppliers().registerEventAppliers(state);
    final var writers = new Writers(() -> resultBuilder, eventAppliers);

    resultBuilder = new FakeProcessingResultBuilder<>();
    processor = new DeploymentReconstructProcessor(keyGenerator, state, writers);
  }

  @Test
  void shouldEndReconstructionOnEmptyState() {
    // given

    final var command = mock(TypedRecord.class);
    when(command.getValue()).thenReturn(new DeploymentRecord());

    // when
    processor.processRecord(command);

    // then
    Assertions.assertThat(resultBuilder.getFollowupRecords())
        .singleElement()
        .satisfies(
            record ->
                Assertions.assertThat(record.getIntent())
                    .isEqualTo(DeploymentIntent.RECONSTRUCTED_ALL));
  }

  @Test
  void shouldIgnoreProcessWithExistingDeployment() {
    // given
    final var command = mock(TypedRecord.class);
    when(command.getValue()).thenReturn(new DeploymentRecord());

    final var deploymentKey = Protocol.encodePartitionId(1, 1);
    final var processKey = Protocol.encodePartitionId(1, 2);
    state
        .getProcessState()
        .putProcess(
            processKey,
            new ProcessRecord()
                .setDeploymentKey(deploymentKey)
                .setBpmnProcessId("process")
                .setResourceName("process.bpmn")
                .setVersion(1));
    state.getDeploymentState().storeDeploymentRecord(deploymentKey, new DeploymentRecord());

    // when
    processor.processRecord(command);

    // then
    Assertions.assertThat(resultBuilder.getFollowupRecords())
        .singleElement()
        .satisfies(
            record ->
                Assertions.assertThat(record.getIntent())
                    .isEqualTo(DeploymentIntent.RECONSTRUCTED_ALL));
  }

  @Test
  void shouldReconstructForSingleProcessWithoutDeploymentKey() {
    // given
    final var command = mock(TypedRecord.class);
    when(command.getValue()).thenReturn(new DeploymentRecord());

    final var deploymentKey = Protocol.encodePartitionId(1, 1);
    final var processKey = Protocol.encodePartitionId(1, 2);
    state
        .getProcessState()
        .putProcess(
            processKey,
            new ProcessRecord()
                .setDeploymentKey(deploymentKey)
                .setBpmnProcessId("process")
                .setResourceName("process.bpmn")
                .setVersion(1));

    // when
    processor.processRecord(command);

    // then
    Assertions.assertThat(resultBuilder.getFollowupRecords())
        .singleElement()
        .satisfies(
            record ->
                Assertions.assertThat(record.getIntent())
                    .isEqualTo(DeploymentIntent.RECONSTRUCTED));
  }
}
