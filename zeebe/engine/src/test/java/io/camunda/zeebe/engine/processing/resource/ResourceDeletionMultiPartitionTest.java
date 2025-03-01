/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ResourceDeletionMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;
  private static final String DMN_RESOURCE = "/dmn/decision-table.dmn";
  private static final String FORM_RESOURCE = "/form/test-form-1.form";
  private static final String RPA_RESOURCE = "/resource/test-rpa-1.rpa";

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldTestDmnLifecycle() {
    // given
    engine.deployment().withXmlClasspathResource(DMN_RESOURCE).deploy();

    final var resourceKey =
        RecordingExporter.decisionRequirementsRecords()
            .withIntent(DecisionRequirementsIntent.CREATED)
            .withDecisionRequirementsId("force_users")
            .getFirst()
            .getKey();

    // when
    engine.resourceDeletion().withResourceKey(resourceKey).delete();

    // then
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 2))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                // We want to verify the partition id where the deletion was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .containsSubsequence(
            tuple(ResourceDeletionIntent.DELETE, RecordType.COMMAND, 1),
            tuple(DecisionIntent.DELETED, RecordType.EVENT, 1),
            tuple(DecisionRequirementsIntent.DELETED, RecordType.EVENT, 1),
            tuple(ResourceDeletionIntent.DELETED, RecordType.EVENT, 1),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));

    for (int partitionId = 2; partitionId < PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.records()
                  .withPartitionId(partitionId)
                  .limit(r -> r.getIntent().equals(ResourceDeletionIntent.DELETED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .endsWith(
              ResourceDeletionIntent.DELETE,
              ResourceDeletionIntent.DELETING,
              DecisionIntent.DELETED,
              DecisionRequirementsIntent.DELETED,
              ResourceDeletionIntent.DELETED);
    }
  }

  @Test
  public void shouldTestBpmnLifecycle() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final long resourceKey =
        engine
            .deployment()
            .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();

    // when
    engine.resourceDeletion().withResourceKey(resourceKey).delete();

    // then
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 2))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                // We want to verify the partition id where the deletion was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .containsSubsequence(
            tuple(ResourceDeletionIntent.DELETE, RecordType.COMMAND, 1),
            tuple(ProcessIntent.DELETING, RecordType.EVENT, 1),
            tuple(ProcessIntent.DELETED, RecordType.EVENT, 1),
            tuple(ResourceDeletionIntent.DELETED, RecordType.EVENT, 1),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));

    for (int partitionId = 2; partitionId < PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.records()
                  .withPartitionId(partitionId)
                  .limit(r -> r.getIntent().equals(ResourceDeletionIntent.DELETED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .endsWith(
              ResourceDeletionIntent.DELETE,
              ResourceDeletionIntent.DELETING,
              ProcessIntent.DELETING,
              ProcessIntent.DELETED,
              ResourceDeletionIntent.DELETED);
    }
  }

  @Test
  public void shouldTestFormLifecycle() {
    // given
    final var resourceKey =
        engine
            .deployment()
            .withXmlClasspathResource(FORM_RESOURCE)
            .deploy()
            .getValue()
            .getFormMetadata()
            .get(0)
            .getFormKey();

    // when
    engine.resourceDeletion().withResourceKey(resourceKey).delete();

    // then
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 2))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                // We want to verify the partition id where the deletion was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .containsSubsequence(
            tuple(ResourceDeletionIntent.DELETE, RecordType.COMMAND, 1),
            tuple(FormIntent.DELETED, RecordType.EVENT, 1),
            tuple(ResourceDeletionIntent.DELETED, RecordType.EVENT, 1),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));

    for (int partitionId = 2; partitionId < PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.records()
                  .withPartitionId(partitionId)
                  .limit(r -> r.getIntent().equals(ResourceDeletionIntent.DELETED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .endsWith(
              ResourceDeletionIntent.DELETE,
              ResourceDeletionIntent.DELETING,
              FormIntent.DELETED,
              ResourceDeletionIntent.DELETED);
    }
  }

  @Test
  public void shouldTestResourceLifecycle() {
    // given
    final var resourceKey =
        engine
            .deployment()
            .withJsonClasspathResource(RPA_RESOURCE)
            .deploy()
            .getValue()
            .getResourceMetadata()
            .getFirst()
            .getResourceKey();

    // when
    engine.resourceDeletion().withResourceKey(resourceKey).delete();

    // then
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 2))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                // We want to verify the partition id where the deletion was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .containsSubsequence(
            tuple(ResourceDeletionIntent.DELETE, RecordType.COMMAND, 1),
            tuple(ResourceIntent.DELETED, RecordType.EVENT, 1),
            tuple(ResourceDeletionIntent.DELETED, RecordType.EVENT, 1),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));

    for (int partitionId = 2; partitionId < PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.records()
                  .withPartitionId(partitionId)
                  .limit(r -> r.getIntent().equals(ResourceDeletionIntent.DELETED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .endsWith(
              ResourceDeletionIntent.DELETE,
              ResourceDeletionIntent.DELETING,
              ResourceIntent.DELETED,
              ResourceDeletionIntent.DELETED);
    }
  }
}
