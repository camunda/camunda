/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.historydeletion;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.HistoryDeletionConfig;
import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel;
import io.camunda.db.rdbms.write.service.HistoryDeletionService;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.VariableFixtures;
import io.camunda.it.rdbms.db.history.ProcessInstanceHistory;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.time.Duration;
import java.time.InstantSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class HistoryDeletionServiceIT extends ProcessInstanceHistory {
  @TestTemplate
  public void shouldExecuteHistoryDeletionForProcessInstanceSuccessfully(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var writers = testApplication.getRdbmsService().createWriter(0);
    final var historyDeletionService =
        new HistoryDeletionService(
            writers,
            rdbmsService.getHistoryDeletionDbReader("default"),
            rdbmsService.getProcessInstanceReader("default"),
            rdbmsService.getDecisionInstanceReader("default"),
            new HistoryDeletionConfig(Duration.ofSeconds(1), Duration.ofMinutes(5), 100, 10000),
            InstantSource.system());

    final long processInstanceKey = ProcessInstanceFixtures.nextKey();
    createRandomProcessWithRelevantRelatedData(
        rdbmsService, writers, b -> b.partitionId(0).processInstanceKey(processInstanceKey));

    final long otherProcessInstanceKey = ProcessInstanceFixtures.nextKey();
    createRandomProcessWithRelevantRelatedData(
        rdbmsService, writers, b -> b.partitionId(0).processInstanceKey(otherProcessInstanceKey));

    processInstanceAndRelatedRecordsExist(rdbmsService, processInstanceKey);
    processInstanceAndRelatedRecordsExist(rdbmsService, otherProcessInstanceKey);

    final HistoryDeletionDbModel deletionDbModel =
        new HistoryDeletionDbModel.Builder()
            .resourceKey(processInstanceKey)
            .resourceType(HistoryDeletionDbModel.HistoryDeletionTypeDbModel.PROCESS_INSTANCE)
            .batchOperationKey(ProcessInstanceFixtures.nextKey())
            .partitionId(0)
            .build();

    writers.getHistoryDeletionWriter().create(deletionDbModel);
    writers.flush();

    // when
    historyDeletionService.deleteHistory(0);

    // then
    processInstanceAndNonAuditLogRelatedRecordsHaveBeenDeleted(rdbmsService, processInstanceKey);
    auditLogsNotDeleted(rdbmsService, processInstanceKey);
    processInstanceAndRelatedRecordsExist(rdbmsService, otherProcessInstanceKey);
  }

  @TestTemplate
  public void shouldExitEarlyIfProcessInstanceDependentChildrenNotFullyDeleted(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var writers = testApplication.getRdbmsService().createWriter(0);
    final var historyDeletionService =
        new HistoryDeletionService(
            writers,
            rdbmsService.getHistoryDeletionDbReader("default"),
            rdbmsService.getProcessInstanceReader("default"),
            rdbmsService.getDecisionInstanceReader("default"),
            new HistoryDeletionConfig(Duration.ofSeconds(1), Duration.ofMinutes(5), 100, 10),
            InstantSource.system());

    final long processInstanceKey = ProcessInstanceFixtures.nextKey();
    createRandomProcessWithRelevantRelatedData(
        rdbmsService, writers, b -> b.partitionId(0).processInstanceKey(processInstanceKey));

    processInstanceAndRelatedRecordsExist(rdbmsService, processInstanceKey);

    final HistoryDeletionDbModel deletionDbModel =
        new HistoryDeletionDbModel.Builder()
            .resourceKey(processInstanceKey)
            .resourceType(HistoryDeletionDbModel.HistoryDeletionTypeDbModel.PROCESS_INSTANCE)
            .batchOperationKey(ProcessInstanceFixtures.nextKey())
            .partitionId(0)
            .build();

    writers.getHistoryDeletionWriter().create(deletionDbModel);
    writers.flush();

    // when
    historyDeletionService.deleteHistory(0);

    // then
    assertThat(processInstanceCount(rdbmsService, processInstanceKey)).isEqualTo(1L);
    assertThat(flowNodeInstanceCount(rdbmsService, processInstanceKey)).isEqualTo(10L);
    assertThat(userTaskCount(rdbmsService, processInstanceKey)).isEqualTo(10L);
    assertThat(variableCount(rdbmsService, processInstanceKey)).isEqualTo(10L);
    assertThat(incidentCount(rdbmsService, processInstanceKey)).isEqualTo(10L);
    assertThat(decisionInstanceCount(rdbmsService, processInstanceKey)).isEqualTo(10L);
    auditLogsNotDeleted(rdbmsService, processInstanceKey);
  }

  @TestTemplate
  public void shouldDeleteVariableNameLookupWhenDeletingProcessDefinition(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var writers = rdbmsService.createWriter(0);
    final var historyDeletionService =
        new HistoryDeletionService(
            writers,
            rdbmsService.getHistoryDeletionDbReader("default"),
            rdbmsService.getProcessInstanceReader("default"),
            rdbmsService.getDecisionInstanceReader("default"),
            new HistoryDeletionConfig(Duration.ofSeconds(1), Duration.ofMinutes(5), 100, 10000),
            InstantSource.system());

    // target process definition — no live process instances so it is eligible for deletion
    final var targetProcDef =
        ProcessDefinitionFixtures.createAndSaveRandomProcessDefinition(writers, b -> b);
    final long targetPdKey = targetProcDef.processDefinitionKey();
    final String varNameA = "var-a-" + nextStringId();
    final String varNameB = "var-b-" + nextStringId();
    VariableFixtures.createAndSaveVariableWithProcessDefinition(
        rdbmsService, VariableFixtures.createRandomized(b -> b.name(varNameA)), targetPdKey);
    VariableFixtures.createAndSaveVariableWithProcessDefinition(
        rdbmsService, VariableFixtures.createRandomized(b -> b.name(varNameB)), targetPdKey);

    // control process definition whose lookup rows must survive (not scheduled for deletion)
    final var controlProcDef =
        ProcessDefinitionFixtures.createAndSaveRandomProcessDefinition(writers, b -> b);
    final long controlPdKey = controlProcDef.processDefinitionKey();
    final String controlVarName = "control-" + nextStringId();
    VariableFixtures.createAndSaveVariableWithProcessDefinition(
        rdbmsService, VariableFixtures.createRandomized(b -> b.name(controlVarName)), controlPdKey);

    // schedule target process definition for deletion (no live process instances)
    final var deletionModel =
        new HistoryDeletionDbModel.Builder()
            .resourceKey(targetPdKey)
            .resourceType(HistoryDeletionDbModel.HistoryDeletionTypeDbModel.PROCESS_DEFINITION)
            .batchOperationKey(ProcessInstanceFixtures.nextKey())
            .partitionId(0)
            .build();
    writers.getHistoryDeletionWriter().create(deletionModel);
    writers.flush();

    // pre-condition: lookup rows exist for the target
    assertThat(rdbmsService.getVariableReader("default").findLookupVariableNames(targetPdKey))
        .containsExactlyInAnyOrder(varNameA, varNameB);

    // when
    historyDeletionService.deleteHistory(0);

    // then: target lookup rows removed; control untouched
    assertThat(rdbmsService.getVariableReader("default").findLookupVariableNames(targetPdKey))
        .isEmpty();
    assertThat(rdbmsService.getVariableReader("default").findLookupVariableNames(controlPdKey))
        .containsExactly(controlVarName);
  }

  private void auditLogsNotDeleted(final RdbmsService rdbmsService, final long processInstanceKey) {
    assertThat(auditLogCount(rdbmsService, processInstanceKey)).isEqualTo(20L);
  }
}
