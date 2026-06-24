/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.historycleanup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.HistoryConfig;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.history.ProcessInstanceHistory;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class HistoryCleanupIT extends ProcessInstanceHistory {

  @TestTemplate
  public void shouldExecuteHistoryCleanupSuccessfully(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var writers = testApplication.getRdbmsService().createWriter(0);
    final var historyCleanupService =
        new HistoryCleanupService(
            RdbmsWriterConfig.builder().build(), writers, rdbmsService.getProcessInstanceReader());

    final long rootProcessInstanceKey = ProcessInstanceFixtures.nextKey();
    createRandomProcessWithRelevantRelatedData(
        rdbmsService,
        writers,
        b ->
            b.partitionId(0)
                .processInstanceKey(rootProcessInstanceKey)
                .rootProcessInstanceKey(rootProcessInstanceKey)
                .historyCleanupDate(OffsetDateTime.now().minusSeconds(1)));

    final long otherRootProcessInstanceKey = ProcessInstanceFixtures.nextKey();
    createRandomProcessWithRelevantRelatedData(
        rdbmsService,
        writers,
        b ->
            b.partitionId(0)
                .processInstanceKey(otherRootProcessInstanceKey)
                .rootProcessInstanceKey(otherRootProcessInstanceKey));

    processInstanceAndRelatedRecordsExist(rdbmsService, rootProcessInstanceKey);
    processInstanceAndRelatedRecordsExist(rdbmsService, otherRootProcessInstanceKey);

    // when
    historyCleanupService.cleanupHistory(0, OffsetDateTime.now());

    // then
    processInstanceAndRelatedRecordsHaveBeenDeleted(rdbmsService, rootProcessInstanceKey);
    processInstanceAndRelatedRecordsExist(rdbmsService, otherRootProcessInstanceKey);
  }

  @TestTemplate
  public void shouldExitEarlyIfDependentChildrenNotFullyDeleted(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var writers = testApplication.getRdbmsService().createWriter(0);
    final var historyCleanupService =
        new HistoryCleanupService(
            RdbmsWriterConfig.builder()
                .history(HistoryConfig.builder().historyCleanupBatchSize(10).build())
                .build(),
            writers,
            rdbmsService.getProcessInstanceReader());

    final long rootProcessInstanceKey = ProcessInstanceFixtures.nextKey();
    createRandomProcessWithRelevantRelatedData(
        rdbmsService,
        writers,
        b ->
            b.partitionId(0)
                .processInstanceKey(rootProcessInstanceKey)
                .rootProcessInstanceKey(rootProcessInstanceKey)
                .historyCleanupDate(OffsetDateTime.now().minusSeconds(1)));

    processInstanceAndRelatedRecordsExist(rdbmsService, rootProcessInstanceKey);

    // when
    historyCleanupService.cleanupHistory(0, OffsetDateTime.now());

    // then
    assertThat(processInstanceCount(rdbmsService, rootProcessInstanceKey)).isEqualTo(1L);
    assertThat(flowNodeInstanceCount(rdbmsService, rootProcessInstanceKey)).isEqualTo(10L);
    assertThat(userTaskCount(rdbmsService, rootProcessInstanceKey)).isEqualTo(10L);
    assertThat(variableCount(rdbmsService, rootProcessInstanceKey)).isEqualTo(10L);
    assertThat(incidentCount(rdbmsService, rootProcessInstanceKey)).isEqualTo(10L);
    assertThat(decisionInstanceCount(rdbmsService, rootProcessInstanceKey)).isEqualTo(10L);
    assertThat(auditLogCount(rdbmsService, rootProcessInstanceKey)).isEqualTo(10L);
  }

  @TestTemplate
  public void shouldDeleteDependentProcesses(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var writers = testApplication.getRdbmsService().createWriter(0);
    final var historyCleanupService =
        new HistoryCleanupService(
            RdbmsWriterConfig.builder().build(), writers, rdbmsService.getProcessInstanceReader());

    final long rootProcessInstanceKey = ProcessInstanceFixtures.nextKey();
    createRandomProcessWithRelevantRelatedData(
        rdbmsService,
        writers,
        b ->
            b.partitionId(0)
                .processInstanceKey(rootProcessInstanceKey)
                .rootProcessInstanceKey(rootProcessInstanceKey)
                .historyCleanupDate(OffsetDateTime.now().minusSeconds(1)));

    final long dependentProcessInstanceKey =
        createRandomProcessWithRelevantRelatedData(
            rdbmsService,
            writers,
            b -> b.partitionId(0).rootProcessInstanceKey(rootProcessInstanceKey));

    processInstanceAndRelatedRecordsExist(rdbmsService, rootProcessInstanceKey);
    processInstanceAndRelatedRecordsExist(rdbmsService, dependentProcessInstanceKey);

    // when
    historyCleanupService.cleanupHistory(0, OffsetDateTime.now());

    // then
    processInstanceAndRelatedRecordsHaveBeenDeleted(rdbmsService, rootProcessInstanceKey);
    processInstanceAndRelatedRecordsHaveBeenDeleted(rdbmsService, dependentProcessInstanceKey);
  }

  @TestTemplate
  public void shouldExitEarlyIfDependentProcessesNotFullyDeleted(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var writers = testApplication.getRdbmsService().createWriter(0);
    final var historyCleanupService =
        new HistoryCleanupService(
            RdbmsWriterConfig.builder()
                .history(HistoryConfig.builder().historyCleanupBatchSize(10).build())
                .build(),
            writers,
            rdbmsService.getProcessInstanceReader());

    final long rootProcessInstanceKey = ProcessInstanceFixtures.nextKey();

    ProcessInstanceFixtures.createAndSaveRandomProcessInstance(
        writers,
        b ->
            b.partitionId(0)
                .processInstanceKey(rootProcessInstanceKey)
                .rootProcessInstanceKey(rootProcessInstanceKey)
                .historyCleanupDate(OffsetDateTime.now().minusSeconds(1)));

    final var dependentProcessInstanceIds = new ArrayList<Long>();
    for (int i = 0; i < 10; i++) {
      final var dependentProcessInstance =
          ProcessInstanceFixtures.createAndSaveRandomProcessInstance(
              writers, b -> b.partitionId(0).rootProcessInstanceKey(rootProcessInstanceKey));
      dependentProcessInstanceIds.add(dependentProcessInstance.processInstanceKey());
    }

    assertThat(processInstanceCount(rdbmsService, rootProcessInstanceKey)).isEqualTo(1L);
    assertThat(processInstanceCount(rdbmsService, dependentProcessInstanceIds)).isEqualTo(10L);

    // when
    historyCleanupService.cleanupHistory(0, OffsetDateTime.now());

    // then
    assertThat(processInstanceCount(rdbmsService, rootProcessInstanceKey)).isEqualTo(1L);
    assertThat(processInstanceCount(rdbmsService, dependentProcessInstanceIds)).isEqualTo(0L);
  }

  @TestTemplate
  public void shouldIgnoreNonRootProcessInstancesWithCleanupDateSet(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var writers = testApplication.getRdbmsService().createWriter(0);
    final var historyCleanupService =
        new HistoryCleanupService(
            RdbmsWriterConfig.builder().build(), writers, rdbmsService.getProcessInstanceReader());

    final long processInstanceKey =
        createRandomProcessWithRelevantRelatedData(
            rdbmsService,
            writers,
            b -> b.partitionId(0).historyCleanupDate(OffsetDateTime.now().minusSeconds(1)));

    // when
    historyCleanupService.cleanupHistory(0, OffsetDateTime.now());

    // then
    processInstanceAndRelatedRecordsExist(rdbmsService, processInstanceKey);
  }
}
