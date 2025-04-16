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
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord.ReconstructionProgress;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
    final var processKeys =
        Stream.of(2, 3).mapToLong(idx -> Protocol.encodePartitionId(1, idx)).toArray();
    for (int i = 0; i < 2; i++) {
      state
          .getProcessState()
          .putProcess(
              processKeys[i],
              new ProcessRecord()
                  .setDeploymentKey(deploymentKey)
                  .setBpmnProcessId(String.format("process%d", i + 1))
                  .setResourceName(String.format("process%d.bpmn", i + 1))
                  .setResource(BufferUtil.wrapString(String.format("process resource #%d", i + 1)))
                  .setVersion(1));
    }

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

                    assertThat(deploymentRecord.getResources()).hasSize(2);
                    assertThat(
                            deploymentRecord.getResources().stream()
                                .sorted(Comparator.comparing(DeploymentResource::getResourceName)))
                        .zipSatisfy(
                            List.of(1, 2),
                            (resource, index) -> {
                              assertThat(resource.getResourceName())
                                  .isEqualTo(String.format("process%d.bpmn", index));
                              assertThat(new String(resource.getResource()))
                                  .isEqualTo(String.format("process resource #%d", index));
                            });
                  });
        });
  }

  @Test
  void shouldReconstructForSingleFormWithoutDeploymentKey() {
    // given
    final var command = mockedCommand();

    final var formKey = Protocol.encodePartitionId(1, 2);
    final var form =
        new FormRecord()
            .setFormKey(formKey)
            .setFormId("form")
            .setResourceName("form.form")
            .setVersion(1);
    state.getFormState().storeFormInFormColumnFamily(form);
    state.getFormState().storeFormInFormByIdAndVersionColumnFamily(form);

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
                .setResource(BufferUtil.wrapString("form resource #1"))
                .setVersion(1));
    state
        .getFormState()
        .storeFormInFormColumnFamily(
            new FormRecord()
                .setDeploymentKey(deploymentKey)
                .setFormKey(formKey2)
                .setFormId("form2")
                .setResourceName("form2.form")
                .setResource(BufferUtil.wrapString("form resource #2"))
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
                    assertThat(deploymentRecord.getResources()).hasSize(2);
                    assertThat(deploymentRecord.getResources())
                        .zipSatisfy(
                            List.of(1, 2),
                            (resource, index) -> {
                              assertThat(resource.getResourceName())
                                  .isEqualTo(String.format("form%d.form", index));
                              assertThat(new String(resource.getResource()))
                                  .isEqualTo(String.format("form resource #%d", index));
                            });
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
            new DecisionRequirementsRecord()
                .setDecisionRequirementsKey(decisionRequirementsKey)
                .setResourceName("decision-resource.decision")
                .setResource(BufferUtil.wrapString("decision requirements binary")));
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
                    assertThat(deploymentRecord.getResources())
                        .singleElement()
                        .satisfies(
                            resource -> {
                              assertThat(new String(resource.getResource()))
                                  .isEqualTo("decision requirements binary");
                              assertThat(resource.getResourceName())
                                  .isEqualTo("decision-resource.decision");
                            });
                  });
        });
  }

  private TypedRecord<DeploymentRecord> mockedCommand() {
    final var command = mock(TypedRecord.class);
    when(command.getValue()).thenReturn(DeploymentRecord.emptyCommandForReconstruction());
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

  @Test
  public void shouldUseRangeQueriesWhenReconstructing() {
    // given
    final var command = mock(TypedRecord.class);
    when(command.getValue()).thenReturn(new DeploymentRecord());
    final var processKey1 = Protocol.encodePartitionId(1, 2);
    final var processKey2 = Protocol.encodePartitionId(1, 3);

    final var formDeploymentKey = Protocol.encodePartitionId(1, 4);
    final var formKey = Protocol.encodePartitionId(1, 5);

    final var decisionDeploymentKey = Protocol.encodePartitionId(1, 5);
    final var decisionKey = Protocol.encodePartitionId(1, 7);
    final var decisionRequirementsKey = Protocol.encodePartitionId(1, 8);

    int eventIndex = 0;
    state
        .getProcessState()
        .putProcess(
            processKey1,
            new ProcessRecord()
                .setBpmnProcessId("process")
                .setResourceName("process.bpmn")
                .setResource(BufferUtil.wrapString("process binary resource"))
                .setTenantId("tenant-id")
                .setVersion(1));

    state
        .getProcessState()
        .putProcess(
            processKey2,
            new ProcessRecord()
                .setBpmnProcessId("process2")
                .setResourceName("process2.bpmn")
                .setResource(BufferUtil.wrapString("process binary resource #2"))
                .setTenantId("tenant-id")
                .setVersion(2));
    state
        .getFormState()
        .storeFormInFormColumnFamily(
            new FormRecord()
                .setDeploymentKey(formDeploymentKey)
                .setFormKey(formKey)
                .setFormId("form")
                .setTenantId("form-tenant")
                .setResourceName("form.form")
                .setResource(BufferUtil.wrapString("form binary resource"))
                .setVersion(1));
    state
        .getDecisionState()
        .storeDecisionRequirements(
            new DecisionRequirementsRecord()
                .setTenantId("decision-tenant")
                .setResourceName("decision-resource.decision")
                .setResource(BufferUtil.wrapString("decision requirement binary resource"))
                .setDecisionRequirementsKey(decisionRequirementsKey));
    state
        .getDecisionState()
        .storeDecisionRecord(
            new DecisionRecord()
                .setDeploymentKey(decisionDeploymentKey)
                .setDecisionKey(decisionKey)
                .setDecisionId("decision2")
                .setDecisionName("decision2")
                .setTenantId("decision-tenant")
                .setDecisionRequirementsKey(decisionRequirementsKey)
                .setVersion(1));
    // when
    processor.processRecord(command);

    // then
    assertThat(getCommandAt(eventIndex++).getValue())
        .satisfies(
            record -> {
              assertThat(record.processesMetadata())
                  .singleElement()
                  .satisfies(el -> assertThat(el.getBpmnProcessId()).isEqualTo("process"));
              assertThat(record.decisionsMetadata()).isEmpty();
              assertThat(record.formMetadata()).isEmpty();
              assertThat(record.getResources())
                  .singleElement()
                  .satisfies(
                      resource -> {
                        assertThat(resource.getResourceName()).isEqualTo("process.bpmn");
                        assertThat(new String(resource.getResource()))
                            .isEqualTo("process binary resource");
                      });
            });

    // when
    processor.processRecord(getCommandAt(eventIndex++));

    // then
    assertThat(getCommandAt(eventIndex++).getValue())
        .satisfies(
            record -> {
              assertThat(record.processesMetadata())
                  .singleElement()
                  .satisfies(el -> assertThat(el.getBpmnProcessId()).isEqualTo("process2"));
              assertThat(record.decisionsMetadata()).isEmpty();
              assertThat(record.formMetadata()).isEmpty();
              assertThat(record.getResources())
                  .singleElement()
                  .satisfies(
                      resource -> {
                        assertThat(resource.getResourceName()).isEqualTo("process2.bpmn");
                        assertThat(new String(resource.getResource()))
                            .isEqualTo("process binary resource #2");
                      });
            });

    // when
    processor.processRecord(getCommandAt(eventIndex++));

    // then
    assertThat(getCommandAt(eventIndex++).getValue())
        .satisfies(
            record -> {
              assertThat(record.processesMetadata()).isEmpty();
              assertThat(record.decisionsMetadata()).isEmpty();
              assertThat(record.formMetadata())
                  .singleElement()
                  .satisfies(
                      el -> {
                        assertThat(el.getFormKey()).isEqualTo(formKey);
                        assertThat(record.getResources())
                            .singleElement()
                            .satisfies(
                                resource -> {
                                  assertThat(resource.getResourceName()).isEqualTo("form.form");
                                  assertThat(new String(resource.getResource()))
                                      .isEqualTo("form binary resource");
                                });
                      });
            });

    // when
    processor.processRecord(getCommandAt(eventIndex++));

    // then
    assertThat(getCommandAt(eventIndex++).getValue())
        .satisfies(
            record -> {
              assertThat(record.processesMetadata()).isEmpty();
              assertThat(record.formMetadata()).isEmpty();
              assertThat(record.decisionsMetadata())
                  .singleElement()
                  .satisfies(
                      el -> {
                        assertThat(el.getDecisionKey()).isEqualTo(decisionKey);
                        final var resource = record.getResources().getFirst();
                        assertThat(resource.getResourceName())
                            .isEqualTo("decision-resource.decision");
                        assertThat(new String(resource.getResource()))
                            .isEqualTo("decision requirement binary resource");
                      });
            });

    // when
    // let's run again the processor to make it reach progressState = Done
    processor.processRecord(getCommandAt(eventIndex++));
  }

  private TypedRecord<DeploymentRecord> getCommandAt(final int index) {
    assertThat(resultBuilder.getFollowupRecords()).hasSizeGreaterThanOrEqualTo(index + 1);
    final var record = resultBuilder.getFollowupRecords().get(index);
    assertThat(record.getValue()).isInstanceOf(DeploymentRecord.class);
    final var command = mockedCommand();
    when(command.getValue()).thenReturn((DeploymentRecord) record.getValue());
    when(command.getIntent()).thenReturn(record.getIntent());
    when(command.getRecordType()).thenReturn(record.getRecordType());
    return command;
  }

  @Nested
  class ReconstructionProgressTest {
    @Test
    void shouldHaveDoneAsTerminalState() {
      var progress = ReconstructionProgress.PROCESS;
      for (int i = 0; i < 3; i++) {
        progress = DeploymentReconstructProcessor.nextProgress(progress);
      }
      assertThat(progress).isEqualTo(ReconstructionProgress.DONE);
      assertThat(DeploymentReconstructProcessor.nextProgress(progress))
          .isEqualTo(ReconstructionProgress.DONE);
    }
  }
}
