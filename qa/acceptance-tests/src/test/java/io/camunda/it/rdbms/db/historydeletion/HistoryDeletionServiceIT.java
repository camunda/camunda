/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.historydeletion;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.HistoryDeletionConfig;
import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel;
import io.camunda.db.rdbms.write.service.HistoryDeletionService;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.history.ProcessInstanceHistory;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.time.Duration;
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
            rdbmsService.getHistoryDeletionDbReader(),
            rdbmsService.getProcessInstanceReader(),
            rdbmsService.getDecisionInstanceReader(),
            new HistoryDeletionConfig(Duration.ofSeconds(1), Duration.ofMinutes(5), 100, 10000));

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
            rdbmsService.getHistoryDeletionDbReader(),
            rdbmsService.getProcessInstanceReader(),
            rdbmsService.getDecisionInstanceReader(),
            new HistoryDeletionConfig(Duration.ofSeconds(1), Duration.ofMinutes(5), 100, 10));

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

  private void auditLogsNotDeleted(final RdbmsService rdbmsService, final long processInstanceKey) {
    assertThat(auditLogCount(rdbmsService, processInstanceKey)).isEqualTo(20L);
  }
}
