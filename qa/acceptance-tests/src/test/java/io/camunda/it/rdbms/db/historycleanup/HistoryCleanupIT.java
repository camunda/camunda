/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.historycleanup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.ProcessInstanceDbModelBuilder;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.it.rdbms.db.fixtures.AuditLogFixtures;
import io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.ElementInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.IncidentFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.UserTaskFixtures;
import io.camunda.it.rdbms.db.fixtures.VariableFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.clients.reader.SearchEntityReader;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import java.time.OffsetDateTime;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class HistoryCleanupIT {

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
    createRandomProcessWithCleanupRelevantData(
        rdbmsService,
        writers,
        b ->
            b.partitionId(0)
                .processInstanceKey(rootProcessInstanceKey)
                .rootProcessInstanceKey(rootProcessInstanceKey)
                .historyCleanupDate(OffsetDateTime.now().minusSeconds(1)));

    final long otherRootProcessInstanceKey = ProcessInstanceFixtures.nextKey();
    createRandomProcessWithCleanupRelevantData(
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
  public void shouldDeleteDependentProcesses(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var writers = testApplication.getRdbmsService().createWriter(0);
    final var historyCleanupService =
        new HistoryCleanupService(
            RdbmsWriterConfig.builder().build(), writers, rdbmsService.getProcessInstanceReader());

    final long rootProcessInstanceKey = ProcessInstanceFixtures.nextKey();
    createRandomProcessWithCleanupRelevantData(
        rdbmsService,
        writers,
        b ->
            b.partitionId(0)
                .processInstanceKey(rootProcessInstanceKey)
                .rootProcessInstanceKey(rootProcessInstanceKey)
                .historyCleanupDate(OffsetDateTime.now().minusSeconds(1)));

    final long dependentProcessInstanceKey =
        createRandomProcessWithCleanupRelevantData(
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
  public void shouldIgnoreNonRootProcessInstancesWithCleanupDateSet(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var writers = testApplication.getRdbmsService().createWriter(0);
    final var historyCleanupService =
        new HistoryCleanupService(
            RdbmsWriterConfig.builder().build(), writers, rdbmsService.getProcessInstanceReader());

    final long processInstanceKey =
        createRandomProcessWithCleanupRelevantData(
            rdbmsService,
            writers,
            b -> b.partitionId(0).historyCleanupDate(OffsetDateTime.now().minusSeconds(1)));

    // when
    historyCleanupService.cleanupHistory(0, OffsetDateTime.now());

    // then
    processInstanceAndRelatedRecordsExist(rdbmsService, processInstanceKey);
  }

  private void processInstanceAndRelatedRecordsExist(
      final RdbmsService rdbmsService, final long processInstanceKey) {
    assertThat(processInstanceCount(rdbmsService, processInstanceKey)).isEqualTo(1L);
    assertThat(flowNodeInstanceCount(rdbmsService, processInstanceKey)).isEqualTo(20L);
    assertThat(userTaskCount(rdbmsService, processInstanceKey)).isEqualTo(20L);
    assertThat(variableCount(rdbmsService, processInstanceKey)).isEqualTo(20L);
    assertThat(incidentCount(rdbmsService, processInstanceKey)).isEqualTo(20L);
    assertThat(decisionInstanceCount(rdbmsService, processInstanceKey)).isEqualTo(20L);
    assertThat(auditLogCount(rdbmsService, processInstanceKey)).isEqualTo(20L);
  }

  private void processInstanceAndRelatedRecordsHaveBeenDeleted(
      final RdbmsService rdbmsService, final long processInstanceKey) {
    assertThat(processInstanceCount(rdbmsService, processInstanceKey)).isEqualTo(0L);
    assertThat(flowNodeInstanceCount(rdbmsService, processInstanceKey)).isEqualTo(0L);
    assertThat(userTaskCount(rdbmsService, processInstanceKey)).isEqualTo(0L);
    assertThat(variableCount(rdbmsService, processInstanceKey)).isEqualTo(0L);
    assertThat(incidentCount(rdbmsService, processInstanceKey)).isEqualTo(0L);
    assertThat(decisionInstanceCount(rdbmsService, processInstanceKey)).isEqualTo(0L);
    assertThat(auditLogCount(rdbmsService, processInstanceKey)).isEqualTo(0L);
  }

  private long processInstanceCount(
      final RdbmsService rdbmsService, final long processInstanceKey) {
    return entityCount(
        rdbmsService.getProcessInstanceReader(),
        ProcessInstanceQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))));
  }

  private long flowNodeInstanceCount(
      final RdbmsService rdbmsService, final long processInstanceKey) {
    return entityCount(
        rdbmsService.getFlowNodeInstanceReader(),
        FlowNodeInstanceQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))));
  }

  private long userTaskCount(final RdbmsService rdbmsService, final long processInstanceKey) {
    return entityCount(
        rdbmsService.getUserTaskReader(),
        UserTaskQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))));
  }

  private long variableCount(final RdbmsService rdbmsService, final long processInstanceKey) {
    return entityCount(
        rdbmsService.getVariableReader(),
        VariableQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))));
  }

  private long incidentCount(final RdbmsService rdbmsService, final long processInstanceKey) {
    return entityCount(
        rdbmsService.getIncidentReader(),
        IncidentQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))));
  }

  private long decisionInstanceCount(
      final RdbmsService rdbmsService, final long processInstanceKey) {
    return entityCount(
        rdbmsService.getDecisionInstanceReader(),
        DecisionInstanceQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))));
  }

  private long auditLogCount(final RdbmsService rdbmsService, final long processInstanceKey) {
    return entityCount(
        rdbmsService.getAuditLogReader(),
        AuditLogQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))));
  }

  private <T, Q extends TypedSearchQuery<?, ?>> long entityCount(
      final SearchEntityReader<T, Q> entityReader, final Q query) {
    return entityReader.search(query, ResourceAccessChecks.disabled()).total();
  }

  private long createRandomProcessWithCleanupRelevantData(
      final RdbmsService rdbmsService,
      final RdbmsWriters rdbmsWriters,
      final Function<ProcessInstanceDbModelBuilder, ProcessInstanceDbModelBuilder>
          builderFunction) {

    final ProcessInstanceDbModel processInstance =
        ProcessInstanceFixtures.createAndSaveRandomProcessInstance(rdbmsWriters, builderFunction);
    final long processInstanceKey = processInstance.processInstanceKey();
    final long rootProcessInstanceKey = processInstance.rootProcessInstanceKey();

    ElementInstanceFixtures.createAndSaveRandomElementInstances(
        rdbmsWriters,
        b ->
            b.processInstanceKey(processInstanceKey)
                .rootProcessInstanceKey(rootProcessInstanceKey));

    UserTaskFixtures.createAndSaveRandomUserTasks(
        rdbmsService,
        b ->
            b.processInstanceKey(processInstanceKey)
                .rootProcessInstanceKey(rootProcessInstanceKey));

    VariableFixtures.createAndSaveRandomVariables(
        rdbmsService,
        b ->
            b.processInstanceKey(processInstanceKey)
                .rootProcessInstanceKey(rootProcessInstanceKey));

    IncidentFixtures.createAndSaveRandomIncidents(
        rdbmsWriters,
        b ->
            b.processInstanceKey(processInstanceKey)
                .rootProcessInstanceKey(rootProcessInstanceKey));

    DecisionInstanceFixtures.createAndSaveRandomDecisionInstances(
        rdbmsWriters,
        b ->
            b.processInstanceKey(processInstanceKey)
                .rootProcessInstanceKey(rootProcessInstanceKey));

    AuditLogFixtures.createAndSaveRandomAuditLogs(
        rdbmsWriters,
        b ->
            b.processInstanceKey(processInstanceKey)
                .rootProcessInstanceKey(rootProcessInstanceKey));

    return processInstanceKey;
  }
}
