/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.stream.FakeProcessingResultBuilder;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.function.Consumer;
import org.assertj.core.api.InstanceOfAssertFactories;
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
    final var command = mockedCommand();

    // when
    processor.processRecord(command);

    // then
    assertThat(resultBuilder.getFollowupRecords())
        .singleElement()
        .satisfies(
            record -> assertThat(record.getIntent()).isEqualTo(DeploymentIntent.RECONSTRUCTED_ALL));
  }

  @Test
  void shouldIgnoreProcessWithExistingDeployment() {
    // given
    final var command = mockedCommand();

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
    assertThat(resultBuilder.getFollowupRecords())
        .singleElement()
        .satisfies(
            record -> assertThat(record.getIntent()).isEqualTo(DeploymentIntent.RECONSTRUCTED_ALL));
  }

  @Test
  void shouldReconstructForSingleProcessWithoutDeploymentKey() {
    // given
    final var command = mockedCommand();

    final var processKey = Protocol.encodePartitionId(1, 2);
    state
        .getProcessState()
        .putProcess(
            processKey,
            new ProcessRecord()
                .setBpmnProcessId("process")
                .setResourceName("process.bpmn")
                .setResource(
                    BufferUtil.wrapString(
                        Bpmn.convertToString(Bpmn.createExecutableProcess("process").done())))
                .setVersion(1));

    // when
    processor.processRecord(command);

    // then
    assertEventWithFollowupCommand(
        record -> {
          assertThat(record.getIntent()).isEqualTo(DeploymentIntent.RECONSTRUCTED);
          assertThat(record.getKey()).isEqualTo(processKey);
          assertThat(record.getValue())
              .asInstanceOf(InstanceOfAssertFactories.type(DeploymentRecord.class))
              .satisfies(
                  deploymentRecord -> {
                    assertThat(deploymentRecord.getProcessesMetadata()).hasSize(1);
                    assertThat(deploymentRecord.getDeploymentKey()).isEqualTo(processKey);
                  });
        });
  }

  @Test
  void shouldReconstructForSingleProcessWithDeploymentKey() {
    // given
    final var command = mockedCommand();

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
    assertEventWithFollowupCommand(
        record -> {
          assertThat(record.getIntent()).isEqualTo(DeploymentIntent.RECONSTRUCTED);
          assertThat(record.getKey()).isEqualTo(deploymentKey);
          assertThat(record.getValue())
              .asInstanceOf(InstanceOfAssertFactories.type(DeploymentRecord.class))
              .satisfies(
                  deploymentRecord -> {
                    assertThat(deploymentRecord.getProcessesMetadata()).hasSize(1);
                    assertThat(deploymentRecord.getDeploymentKey()).isEqualTo(deploymentKey);
                  });
        });
  }

  @Test
  void shouldIncludeAllProcessesOfDeployment() {
    // given
    final var command = mockedCommand();

    final var deploymentKey = Protocol.encodePartitionId(1, 1);
    final var processKey1 = Protocol.encodePartitionId(1, 2);
    final var processKey2 = Protocol.encodePartitionId(1, 3);
    state
        .getProcessState()
        .putProcess(
            processKey1,
            new ProcessRecord()
                .setDeploymentKey(deploymentKey)
                .setBpmnProcessId("process1")
                .setResourceName("process1.bpmn")
                .setVersion(1));
    state
        .getProcessState()
        .putProcess(
            processKey2,
            new ProcessRecord()
                .setDeploymentKey(deploymentKey)
                .setBpmnProcessId("process2")
                .setResourceName("process2.bpmn")
                .setVersion(1));

    // when
    processor.processRecord(command);

    // then
    assertEventWithFollowupCommand(
        record -> {
          assertThat(record.getIntent()).isEqualTo(DeploymentIntent.RECONSTRUCTED);
          assertThat(record.getKey()).isEqualTo(deploymentKey);
          assertThat(record.getValue())
              .asInstanceOf(InstanceOfAssertFactories.type(DeploymentRecord.class))
              .satisfies(
                  deploymentRecord -> {
                    assertThat(deploymentRecord.getProcessesMetadata()).hasSize(2);
                    assertThat(deploymentRecord.getDeploymentKey()).isEqualTo(deploymentKey);
                  });
        });
  }

  @Test
  void shouldReconstructForSingleFormWithoutDeploymentKey() {
    // given
    final var command = mockedCommand();

    final var formKey = Protocol.encodePartitionId(1, 2);
    state
        .getFormState()
        .storeFormInFormColumnFamily(
            new FormRecord()
                .setFormKey(formKey)
                .setFormId("form")
                .setResourceName("form.form")
                .setVersion(1));

    // when
    processor.processRecord(command);

    // then
    assertEventWithFollowupCommand(
        record -> {
          assertThat(record.getIntent()).isEqualTo(DeploymentIntent.RECONSTRUCTED);
          assertThat(record.getKey()).isEqualTo(formKey);
          assertThat(record.getValue())
              .asInstanceOf(InstanceOfAssertFactories.type(DeploymentRecord.class))
              .satisfies(
                  deploymentRecord -> {
                    assertThat(deploymentRecord.getFormMetadata())
                        .singleElement()
                        .returns(formKey, FormMetadataValue::getFormKey)
                        .returns("form", FormMetadataValue::getFormId)
                        .returns("form.form", FormMetadataValue::getResourceName)
                        .returns(1, FormMetadataValue::getVersion);
                    assertThat(deploymentRecord.getDeploymentKey()).isEqualTo(formKey);
                  });
        });
  }

  @Test
  void shouldReconstructForSingleFormWithDeploymentKey() {
    // given
    final var command = mockedCommand();

    final var deploymentKey = Protocol.encodePartitionId(1, 1);
    final var formKey = Protocol.encodePartitionId(1, 2);
    state
        .getFormState()
        .storeFormInFormColumnFamily(
            new FormRecord()
                .setDeploymentKey(deploymentKey)
                .setFormKey(formKey)
                .setFormId("form")
                .setResourceName("form.form")
                .setVersion(1));

    // when
    processor.processRecord(command);

    // then
    assertEventWithFollowupCommand(
        record -> {
          assertThat(record.getIntent()).isEqualTo(DeploymentIntent.RECONSTRUCTED);
          assertThat(record.getKey()).isEqualTo(deploymentKey);
          assertThat(record.getValue())
              .asInstanceOf(InstanceOfAssertFactories.type(DeploymentRecord.class))
              .satisfies(
                  deploymentRecord -> {
                    assertThat(deploymentRecord.getFormMetadata()).hasSize(1);
                    assertThat(deploymentRecord.getDeploymentKey()).isEqualTo(deploymentKey);
                  });
        });
  }

  @Test
  void shouldIncludeAllFormsOfDeployment() {
    // given
    final var command = mockedCommand();

    final var deploymentKey = Protocol.encodePartitionId(1, 1);
    final var formKey1 = Protocol.encodePartitionId(1, 2);
    final var formKey2 = Protocol.encodePartitionId(1, 3);
    state
        .getFormState()
        .storeFormInFormColumnFamily(
            new FormRecord()
                .setDeploymentKey(deploymentKey)
                .setFormKey(formKey1)
                .setFormId("form1")
                .setResourceName("form1.form")
                .setVersion(1));
    state
        .getFormState()
        .storeFormInFormColumnFamily(
            new FormRecord()
                .setDeploymentKey(deploymentKey)
                .setFormKey(formKey2)
                .setFormId("form2")
                .setResourceName("form2.form")
                .setVersion(1));

    // when
    processor.processRecord(command);

    // then
    assertEventWithFollowupCommand(
        record -> {
          assertThat(record.getIntent()).isEqualTo(DeploymentIntent.RECONSTRUCTED);
          assertThat(record.getKey()).isEqualTo(deploymentKey);
          assertThat(record.getValue())
              .asInstanceOf(InstanceOfAssertFactories.type(DeploymentRecord.class))
              .satisfies(
                  deploymentRecord -> {
                    assertThat(deploymentRecord.getFormMetadata()).hasSize(2);
                    assertThat(deploymentRecord.getDeploymentKey()).isEqualTo(deploymentKey);
                  });
        });
  }

  @Test
  void shouldReconstructForSingleDecisionWithoutDeploymentKey() {
    // given
    final var command = mockedCommand();

    final var decisionRequirementsKey = Protocol.encodePartitionId(1, 2);
    state
        .getDecisionState()
        .storeDecisionRequirements(
            new DecisionRequirementsRecord().setDecisionRequirementsKey(decisionRequirementsKey));
    final var decisionKey = Protocol.encodePartitionId(1, 3);
    state
        .getDecisionState()
        .storeDecisionRecord(
            new DecisionRecord()
                .setDecisionKey(decisionKey)
                .setDecisionId("decision")
                .setDecisionName("decision")
                .setDecisionRequirementsKey(decisionRequirementsKey)
                .setVersion(1));

    // when
    processor.processRecord(command);

    // then
    assertEventWithFollowupCommand(
        record -> {
          assertThat(record.getIntent()).isEqualTo(DeploymentIntent.RECONSTRUCTED);
          assertThat(record.getKey()).isEqualTo(decisionRequirementsKey);
          assertThat(record.getValue())
              .asInstanceOf(InstanceOfAssertFactories.type(DeploymentRecord.class))
              .satisfies(
                  deploymentRecord -> {
                    assertThat(deploymentRecord.getDecisionsMetadata()).hasSize(1);
                    assertThat(deploymentRecord.getDeploymentKey())
                        .isEqualTo(decisionRequirementsKey);
                  });
        });
  }

  @Test
  void shouldReconstructForSingleDecisionWithDeploymentKey() {
    // given
    final var command = mockedCommand();

    final var deploymentKey = Protocol.encodePartitionId(1, 1);
    final var decisionRequirementsKey = Protocol.encodePartitionId(1, 2);
    state
        .getDecisionState()
        .storeDecisionRequirements(
            new DecisionRequirementsRecord().setDecisionRequirementsKey(decisionRequirementsKey));
    final var decisionKey = Protocol.encodePartitionId(1, 3);
    state
        .getDecisionState()
        .storeDecisionRecord(
            new DecisionRecord()
                .setDecisionKey(decisionKey)
                .setDecisionId("decision")
                .setDecisionName("decision")
                .setDecisionRequirementsKey(decisionRequirementsKey)
                .setDeploymentKey(deploymentKey)
                .setVersion(1));

    // when
    processor.processRecord(command);

    // then
    assertEventWithFollowupCommand(
        record -> {
          assertThat(record.getIntent()).isEqualTo(DeploymentIntent.RECONSTRUCTED);
          assertThat(record.getKey()).isEqualTo(deploymentKey);
          assertThat(record.getValue())
              .asInstanceOf(InstanceOfAssertFactories.type(DeploymentRecord.class))
              .satisfies(
                  deploymentRecord -> {
                    assertThat(deploymentRecord.getDecisionsMetadata()).hasSize(1);
                    assertThat(deploymentRecord.getDeploymentKey()).isEqualTo(deploymentKey);
                  });
        });
  }

  @Test
  void shouldIncludeAllDecisionsOfDeployment() {
    // given
    final var command = mockedCommand();

    final var deploymentKey = Protocol.encodePartitionId(1, 1);
    final var decisionRequirementsKey = Protocol.encodePartitionId(1, 2);
    final var decisionKey1 = Protocol.encodePartitionId(1, 3);
    final var decisionKey2 = Protocol.encodePartitionId(1, 4);
    state
        .getDecisionState()
        .storeDecisionRequirements(
            new DecisionRequirementsRecord().setDecisionRequirementsKey(decisionRequirementsKey));
    state
        .getDecisionState()
        .storeDecisionRecord(
            new DecisionRecord()
                .setDeploymentKey(deploymentKey)
                .setDecisionKey(decisionKey1)
                .setDecisionId("decision1")
                .setDecisionName("decision1")
                .setDecisionRequirementsKey(decisionRequirementsKey)
                .setVersion(1));
    state
        .getDecisionState()
        .storeDecisionRecord(
            new DecisionRecord()
                .setDeploymentKey(deploymentKey)
                .setDecisionKey(decisionKey2)
                .setDecisionId("decision2")
                .setDecisionName("decision2")
                .setDecisionRequirementsKey(decisionRequirementsKey)
                .setVersion(1));

    // when
    processor.processRecord(command);

    // then
    assertEventWithFollowupCommand(
        record -> {
          assertThat(record.getIntent()).isEqualTo(DeploymentIntent.RECONSTRUCTED);
          assertThat(record.getKey()).isEqualTo(deploymentKey);
          assertThat(record.getValue())
              .asInstanceOf(InstanceOfAssertFactories.type(DeploymentRecord.class))
              .satisfies(
                  deploymentRecord -> {
                    assertThat(deploymentRecord.getDecisionsMetadata()).hasSize(2);
                    assertThat(deploymentRecord.getDeploymentKey()).isEqualTo(deploymentKey);
                  });
        });
  }

  private TypedRecord<DeploymentRecord> mockedCommand() {
    final var command = mock(TypedRecord.class);
    when(command.getValue()).thenReturn(new DeploymentRecord());
    when(command.getIntent()).thenReturn(DeploymentIntent.RECONSTRUCT);
    return command;
  }

  private void assertEventWithFollowupCommand(
      final Consumer<Record<UnifiedRecordValue>> assertion) {
    assertThat(resultBuilder.getFollowupRecords()).hasSize(2);
    final var event = resultBuilder.getFollowupRecords().get(0);
    assertion.accept(event);
    assertHasFollowupCommand();
  }

  private void assertHasFollowupCommand() {
    final var nextCommand = resultBuilder.getFollowupRecords().get(1);
    assertThat(nextCommand.getRecordType()).isEqualTo(RecordType.COMMAND);
  }
}
