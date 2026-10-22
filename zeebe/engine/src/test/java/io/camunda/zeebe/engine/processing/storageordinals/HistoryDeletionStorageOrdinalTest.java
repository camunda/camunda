/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Asserts the "resourceStorageOrdinal" (the storage ordinal of the resource to delete) on history
 * deletion records. Definition scoped resources carry {@link
 * StorageOrdinalRelated#NOT_ORDINAL_CONTROLLED} on the follow-up command and on the DELETED event,
 * whatever the command carried.
 */
public final class HistoryDeletionStorageOrdinalTest {

  private static final int FIXED_ORDINAL = 1234;
  private static final String DMN_RESOURCE = "/dmn/drg-force-user.dmn";

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinal(FIXED_ORDINAL));

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldMarkProcessDefinitionDeletionAsNotOrdinalControlled() {
    // given
    final var processDefinitionKey = deployProcess();

    // when
    final var deleted =
        engine
            .historyDeletion()
            .processDefinition(processDefinitionKey)
            .withResourceStorageOrdinal(5)
            .delete();

    // then
    assertThat(deleted.getValue().getResourceStorageOrdinal())
        .isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
  }

  @Test
  public void shouldMarkDecisionRequirementsDeletionAsNotOrdinalControlled() {
    // given
    final var decisionRequirementsKey = deployDecisionRequirements();

    // when
    final var deleted =
        engine
            .historyDeletion()
            .decisionRequirements(decisionRequirementsKey)
            .withResourceStorageOrdinal(5)
            .delete();

    // then
    assertThat(deleted.getValue().getResourceStorageOrdinal())
        .isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
  }

  @Test
  public void shouldMarkProcessDefinitionHistoryFollowUpCommandAsNotOrdinalControlled() {
    // given
    final var processDefinitionKey = deployProcess();

    // when
    engine
        .resourceDeletion()
        .withResourceKey(processDefinitionKey)
        .withDeleteHistory(true)
        .delete();

    // then
    final var followUp = readHistoryDeletionFollowUpCommand();
    assertThat(followUp.getResourceType()).isEqualTo(HistoryDeletionType.PROCESS_DEFINITION);
    assertThat(followUp.getResourceStorageOrdinal())
        .isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
  }

  @Test
  public void shouldMarkDecisionRequirementsHistoryFollowUpCommandAsNotOrdinalControlled() {
    // given
    final var decisionRequirementsKey = deployDecisionRequirements();

    // when
    engine
        .resourceDeletion()
        .withResourceKey(decisionRequirementsKey)
        .withDeleteHistory(true)
        .delete();

    // then
    final var followUp = readHistoryDeletionFollowUpCommand();
    assertThat(followUp.getResourceType()).isEqualTo(HistoryDeletionType.DECISION_REQUIREMENTS);
    assertThat(followUp.getResourceStorageOrdinal())
        .isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
  }

  private long deployProcess() {
    return engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(Strings.newRandomValidBpmnId())
                .startEvent()
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private long deployDecisionRequirements() {
    return engine
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .deploy()
        .getValue()
        .getDecisionsMetadata()
        .getFirst()
        .getDecisionRequirementsKey();
  }

  private HistoryDeletionRecordValue readHistoryDeletionFollowUpCommand() {
    return (HistoryDeletionRecordValue)
        RecordingExporter.batchOperationCreationRecords()
            .withIntent(BatchOperationIntent.CREATE)
            .getFirst()
            .getValue()
            .getFollowUpCommand()
            .getRecordValue();
  }
}
