/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.history;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.ProcessInstanceDbModelBuilder;
import io.camunda.it.rdbms.db.fixtures.AuditLogFixtures;
import io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.FlowNodeInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.IncidentFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.UserTaskFixtures;
import io.camunda.it.rdbms.db.fixtures.VariableFixtures;
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
import io.camunda.util.FilterUtil;
import java.util.List;
import java.util.function.Function;

public abstract class ProcessInstanceHistory {

  protected void processInstanceAndRelatedRecordsExist(
      final RdbmsService rdbmsService, final long processInstanceKey) {
    assertThat(processInstanceCount(rdbmsService, processInstanceKey)).isEqualTo(1L);
    assertThat(flowNodeInstanceCount(rdbmsService, processInstanceKey)).isEqualTo(20L);
    assertThat(userTaskCount(rdbmsService, processInstanceKey)).isEqualTo(20L);
    assertThat(variableCount(rdbmsService, processInstanceKey)).isEqualTo(20L);
    assertThat(incidentCount(rdbmsService, processInstanceKey)).isEqualTo(20L);
    assertThat(decisionInstanceCount(rdbmsService, processInstanceKey)).isEqualTo(20L);
    assertThat(auditLogCount(rdbmsService, processInstanceKey)).isEqualTo(20L);
  }

  protected void processInstanceAndRelatedRecordsHaveBeenDeleted(
      final RdbmsService rdbmsService, final long processInstanceKey) {
    processInstanceAndNonAuditLogRelatedRecordsHaveBeenDeleted(rdbmsService, processInstanceKey);
    assertThat(auditLogCount(rdbmsService, processInstanceKey)).isEqualTo(0L);
  }

  protected void processInstanceAndNonAuditLogRelatedRecordsHaveBeenDeleted(
      final RdbmsService rdbmsService, final long processInstanceKey) {
    assertThat(processInstanceCount(rdbmsService, processInstanceKey)).isEqualTo(0L);
    assertThat(flowNodeInstanceCount(rdbmsService, processInstanceKey)).isEqualTo(0L);
    assertThat(userTaskCount(rdbmsService, processInstanceKey)).isEqualTo(0L);
    assertThat(variableCount(rdbmsService, processInstanceKey)).isEqualTo(0L);
    assertThat(incidentCount(rdbmsService, processInstanceKey)).isEqualTo(0L);
    assertThat(decisionInstanceCount(rdbmsService, processInstanceKey)).isEqualTo(0L);
  }

  protected long processInstanceCount(
      final RdbmsService rdbmsService, final long processInstanceKey) {
    return processInstanceCount(rdbmsService, List.of(processInstanceKey));
  }

  protected long processInstanceCount(
      final RdbmsService rdbmsService, final List<Long> processInstanceKeys) {
    return entityCount(
        rdbmsService.getProcessInstanceReader(),
        ProcessInstanceQuery.of(
            b ->
                b.filter(
                    f ->
                        f.processInstanceKeyOperations(
                            FilterUtil.mapDefaultToOperation(processInstanceKeys)))));
  }

  protected long flowNodeInstanceCount(
      final RdbmsService rdbmsService, final long processInstanceKey) {
    return entityCount(
        rdbmsService.getFlowNodeInstanceReader(),
        FlowNodeInstanceQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))));
  }

  protected long userTaskCount(final RdbmsService rdbmsService, final long processInstanceKey) {
    return entityCount(
        rdbmsService.getUserTaskReader(),
        UserTaskQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))));
  }

  protected long variableCount(final RdbmsService rdbmsService, final long processInstanceKey) {
    return entityCount(
        rdbmsService.getVariableReader(),
        VariableQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))));
  }

  protected long incidentCount(final RdbmsService rdbmsService, final long processInstanceKey) {
    return entityCount(
        rdbmsService.getIncidentReader(),
        IncidentQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))));
  }

  protected long decisionInstanceCount(
      final RdbmsService rdbmsService, final long processInstanceKey) {
    return entityCount(
        rdbmsService.getDecisionInstanceReader(),
        DecisionInstanceQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))));
  }

  protected long auditLogCount(final RdbmsService rdbmsService, final long processInstanceKey) {
    return entityCount(
        rdbmsService.getAuditLogReader(),
        AuditLogQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))));
  }

  private <T, Q extends TypedSearchQuery<?, ?>> long entityCount(
      final SearchEntityReader<T, Q> entityReader, final Q query) {
    return entityReader.search(query, ResourceAccessChecks.disabled()).total();
  }

  protected long createRandomProcessWithRelevantRelatedData(
      final RdbmsService rdbmsService,
      final RdbmsWriters rdbmsWriters,
      final Function<ProcessInstanceDbModelBuilder, ProcessInstanceDbModelBuilder>
          builderFunction) {

    final ProcessInstanceDbModel processInstance =
        ProcessInstanceFixtures.createAndSaveRandomProcessInstance(rdbmsWriters, builderFunction);
    final long processInstanceKey = processInstance.processInstanceKey();
    final long rootProcessInstanceKey = processInstance.rootProcessInstanceKey();

    FlowNodeInstanceFixtures.createAndSaveRandomFlowNodeInstances(
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
