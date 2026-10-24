/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.value.ResourceDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.mockito.Mockito;

/**
 * Asserts that resource deletion records carry {@link StorageOrdinalRelated#NOT_ORDINAL_CONTROLLED}
 * on every partition, whatever ordinal the engine assigns to instances.
 */
public final class ResourceDeletionStorageOrdinalTest {

  private static final int FIXED_ORDINAL = 1234;
  private static final int PARTITION_COUNT = 3;
  private static final String DMN_RESOURCE = "/dmn/decision-table.dmn";

  private final SearchClientsProxy searchClientsProxy = Mockito.mock(SearchClientsProxy.class);

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinal(FIXED_ORDINAL))
          .withSearchClientsProxy(searchClientsProxy);

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Before
  public void setUp() {
    when(searchClientsProxy.withSecurityContext(any())).thenReturn(searchClientsProxy);
  }

  @Test
  public void shouldMarkProcessDefinitionDeletionAsNotOrdinalControlled() {
    // given
    final var processDefinitionKey = deployProcess(Strings.newRandomValidBpmnId());

    // when
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // then
    // the DELETE command on partition 1 is the client's, written before the processor ran
    final var partitionOneRecords =
        RecordingExporter.resourceDeletionRecords()
            .withPartitionId(1)
            .onlyEvents()
            .limit(r -> r.getIntent() == ResourceDeletionIntent.DELETED)
            .toList();
    assertThat(partitionOneRecords).isNotEmpty();
    assertThat(partitionOneRecords)
        .allSatisfy(ResourceDeletionStorageOrdinalTest::assertNotOrdinalControlled);

    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      final var distributedRecords =
          RecordingExporter.resourceDeletionRecords()
              .withPartitionId(partitionId)
              .limit(r -> r.getIntent() == ResourceDeletionIntent.DELETED)
              .toList();
      assertThat(distributedRecords)
          .describedAs("records on partition %d", partitionId)
          .extracting(Record::getIntent)
          .contains(
              ResourceDeletionIntent.DELETE,
              ResourceDeletionIntent.DELETING,
              ResourceDeletionIntent.DELETED);
      assertThat(distributedRecords)
          .allSatisfy(ResourceDeletionStorageOrdinalTest::assertNotOrdinalControlled);
    }
  }

  @Test
  public void shouldMarkDecisionRequirementsDeletionAsNotOrdinalControlled() {
    // given
    engine.deployment().withXmlClasspathResource(DMN_RESOURCE).deploy();
    final var decisionRequirementsKey =
        RecordingExporter.decisionRequirementsRecords()
            .withIntent(DecisionRequirementsIntent.CREATED)
            .withDecisionRequirementsId("force_users")
            .getFirst()
            .getKey();

    // when
    engine.resourceDeletion().withResourceKey(decisionRequirementsKey).delete();

    // then
    final var deleted =
        RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
            .withPartitionId(1)
            .getFirst();
    assertThat(deleted.getValue().getStorageOrdinal())
        .isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
  }

  @Test
  public void shouldMarkRejectionOfUnknownResourceAsNotOrdinalControlled() {
    // given
    final long unknownResourceKey = 987_654_321L;

    // when
    final var rejection =
        engine.resourceDeletion().withResourceKey(unknownResourceKey).expectRejection().delete();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getValue().getStorageOrdinal())
        .isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
  }

  @Test
  public void shouldKeepBatchOperationOrdinalWhenDeletingHistory() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var processDefinitionKey = deployProcess(processId);
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    when(searchClientsProxy.searchProcessInstances(Mockito.any(ProcessInstanceQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<ProcessInstanceEntity>()
                .items(
                    List.of(createProcessInstanceEntity(processInstanceKey, processDefinitionKey)))
                .total(1)
                .build());
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(1))
        .hasSize(1);

    // when
    engine
        .resourceDeletion()
        .withResourceKey(processDefinitionKey)
        .withDeleteHistory(true)
        .delete();

    // then
    final var deleted =
        RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
            .withPartitionId(1)
            .getFirst();
    final var batchOperationCreated =
        RecordingExporter.batchOperationCreationRecords()
            .withIntent(BatchOperationIntent.CREATED)
            .getFirst();
    assertThat(deleted.getValue().getStorageOrdinal())
        .isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
    assertThat(batchOperationCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  private long deployProcess(final String processId) {
    return engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static void assertNotOrdinalControlled(final Record<ResourceDeletionRecordValue> record) {
    assertThat(record.getValue().getStorageOrdinal())
        .describedAs("%s %s", record.getRecordType(), record.getIntent())
        .isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
  }

  private ProcessInstanceEntity createProcessInstanceEntity(
      final long processInstanceKey, final long processDefinitionKey) {
    return new ProcessInstanceEntity(
        processInstanceKey,
        null, // rootProcessInstanceKey
        "fake-process-def-id", // processDefinitionId
        null, // processDefinitionName
        -1, // processDefinitionVersion
        null, // processDefinitionVersionTag
        processDefinitionKey,
        -1L, // parentProcessInstanceKey
        -1L, // parentFlowNodeInstanceKey
        OffsetDateTime.now(), // startDate
        null, // endDate
        ProcessInstanceEntity.ProcessInstanceState.ACTIVE, // state
        false, // hasIncident
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        null, // treePath
        Set.of(), // tags
        null, // businessId
        null); // suspendedDate
  }
}
