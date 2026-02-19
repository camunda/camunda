/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.exporter;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.exporter.RecordFixtures.NO_PARENT_EXISTS_KEY;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.LiquibaseSchemaManager;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.exporter.rdbms.RdbmsExporterWrapper;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.filter.UserFilter.Builder;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.GlobalJobStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SequenceFlowQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationItemValue;
import io.camunda.zeebe.protocol.record.value.ImmutableEvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobMetricsValue;
import io.camunda.zeebe.protocol.record.value.ImmutableStatusMetricsValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.StatusMetricsValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.test.util.Strings;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@Tag("rdbms")
@SpringBootTest(classes = {RdbmsTestConfiguration.class})
@TestPropertySource(
    properties = {
      "spring.liquibase.enabled=false",
      "camunda.data.secondary-storage.type=rdbms",
      "camunda.data.secondary-storage.rdbms.queue-size=0",
    })
class RdbmsExporterIT {

  private static final RecordFixtures FIXTURES = new RecordFixtures();
  private final ExporterTestController controller = new ExporterTestController();
  private final VendorDatabaseProperties vendorDatabaseProperties =
      new VendorDatabaseProperties(
          new Properties() {
            {
              setProperty("variableValue.previewSize", "100");
              setProperty("userCharColumn.size", "50");
              setProperty("errorMessage.size", "500");
              setProperty("treePath.size", "500");
              setProperty("disableFkBeforeTruncate", "true");
            }
          });
  @Autowired private LiquibaseSchemaManager liquibaseSchemaManager;
  @Autowired private RdbmsService rdbmsService;
  private RdbmsExporterWrapper exporter;

  @BeforeEach
  void setUp() {
    exporter =
        new RdbmsExporterWrapper(rdbmsService, liquibaseSchemaManager, vendorDatabaseProperties);
    exporter.configure(
        new ExporterContext(
            null,
            new ExporterConfiguration("foo", Map.of("queueSize", 0)),
            1,
            Mockito.mock(MeterRegistry.class, Mockito.RETURNS_DEEP_STUBS),
            null));
    exporter.open(controller);
  }

  @Test
  public void shouldExportProcessInstance() {
    // given
    final var processInstanceRecord = FIXTURES.getProcessInstanceStartedRecord();

    // when
    exporter.export(processInstanceRecord);

    // then
    final var key =
        ((ProcessInstanceRecordValue) processInstanceRecord.getValue()).getProcessInstanceKey();
    final var processInstance = rdbmsService.getProcessInstanceReader().findOne(key);
    assertThat(processInstance).isNotEmpty();
    verifyRootProcessInstanceKey(processInstance.get(), processInstanceRecord);

    // given
    final var processInstanceCompletedRecord = FIXTURES.getProcessInstanceCompletedRecord(key);

    // when
    exporter.export(processInstanceCompletedRecord);

    // then
    final var completedProcessInstance = rdbmsService.getProcessInstanceReader().findOne(key);
    assertThat(completedProcessInstance).isNotEmpty();
    assertThat(completedProcessInstance.get().state()).isEqualTo(ProcessInstanceState.COMPLETED);
    verifyRootProcessInstanceKey(completedProcessInstance.get(), processInstanceRecord);
  }

  @Test
  public void shouldExportRootProcessInstance() {
    // given
    final var rootProcessInstanceRecord =
        FIXTURES.getProcessInstanceStartedRecord(NO_PARENT_EXISTS_KEY);

    // when
    exporter.export(rootProcessInstanceRecord);

    // then
    final var key =
        ((ProcessInstanceRecordValue) rootProcessInstanceRecord.getValue()).getProcessInstanceKey();
    final var processInstance = rdbmsService.getProcessInstanceReader().findOne(key);
    assertThat(processInstance).isNotEmpty();
    verifyRootProcessInstanceKey(processInstance.get(), rootProcessInstanceRecord);

    // given
    final var rootProcessInstanceCompletedRecord =
        FIXTURES.getProcessInstanceCompletedRecord(key, NO_PARENT_EXISTS_KEY);

    // when
    exporter.export(rootProcessInstanceCompletedRecord);

    // then
    final var rootCompletedProcessInstance = rdbmsService.getProcessInstanceReader().findOne(key);
    assertThat(rootCompletedProcessInstance).isNotEmpty();
    assertThat(rootCompletedProcessInstance.get().state())
        .isEqualTo(ProcessInstanceState.COMPLETED);
    assertThat(rootCompletedProcessInstance.get().parentProcessInstanceKey()).isNull();
    assertThat(rootCompletedProcessInstance.get().parentFlowNodeInstanceKey()).isNull();
    verifyRootProcessInstanceKey(rootCompletedProcessInstance.get(), rootProcessInstanceRecord);
  }

  @Test
  public void shouldExportProcessDefinition() {
    // given
    final var processDefinitionRecord = FIXTURES.getProcessDefinitionCreatedRecord();

    // when
    exporter.export(processDefinitionRecord);

    // then
    final var key = ((Process) processDefinitionRecord.getValue()).getProcessDefinitionKey();
    final var processDefinition = rdbmsService.getProcessDefinitionReader().findOne(key);
    assertThat(processDefinition).isNotEmpty();
    assertThat(processDefinition).map(ProcessDefinitionEntity::bpmnXml).isPresent();
    assertThat(processDefinition).map(ProcessDefinitionEntity::formId).contains("test");
  }

  @Test
  public void shouldExportVariables() {
    // given
    final Record<RecordValue> variableCreatedRecord =
        ImmutableRecord.builder()
            .from(RecordFixtures.FACTORY.generateRecord(ValueType.VARIABLE))
            .withIntent(VariableIntent.CREATED)
            .withPosition(2L)
            .withTimestamp(System.currentTimeMillis())
            .build();

    // when
    exporter.export(variableCreatedRecord);

    // then
    final var variable = rdbmsService.getVariableReader().findOne(variableCreatedRecord.getKey());
    final VariableRecordValue variableRecordValue =
        (VariableRecordValue) variableCreatedRecord.getValue();
    assertThat(variable).isNotNull();
    assertThat(variable.value()).isEqualTo(variableRecordValue.getValue());
    assertThat(variable.processInstanceKey())
        .isEqualTo(variableRecordValue.getProcessInstanceKey());
    assertThat(variable.rootProcessInstanceKey())
        .isEqualTo(variableRecordValue.getRootProcessInstanceKey());
  }

  @Test
  public void shouldExportGlobalClusterVariables() {
    // given
    final Record<RecordValue> clusterVariableCreatedRecord =
        FIXTURES.getGlobalClusterVariableRecord(ClusterVariableIntent.CREATED);

    // when
    exporter.export(clusterVariableCreatedRecord);

    final ClusterVariableRecordValue clusterVariableRecordValue =
        (ClusterVariableRecordValue) clusterVariableCreatedRecord.getValue();

    // then
    final var variable =
        rdbmsService
            .getClusterVariableReader()
            .getGloballyScopedClusterVariable(
                clusterVariableRecordValue.getName(),
                ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    assertThat(variable).isNotNull();
    assertThat(variable.value()).isEqualTo(clusterVariableRecordValue.getValue());
    assertThat(variable.name()).isEqualTo(clusterVariableRecordValue.getName());
    assertThat(variable.scope().toString())
        .isEqualTo(clusterVariableRecordValue.getScope().toString());
  }

  @Test
  public void shouldExportTenantClusterVariables() {
    // given
    final Record<RecordValue> clusterVariableCreatedRecord =
        FIXTURES.getTenantClusterVariableRecord("tenant-1", ClusterVariableIntent.CREATED);

    // when
    exporter.export(clusterVariableCreatedRecord);

    final ClusterVariableRecordValue clusterVariableRecordValue =
        (ClusterVariableRecordValue) clusterVariableCreatedRecord.getValue();

    // then
    final var variable =
        rdbmsService
            .getClusterVariableReader()
            .getTenantScopedClusterVariable(
                clusterVariableRecordValue.getName(),
                clusterVariableRecordValue.getTenantId(),
                ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    assertThat(variable).isNotNull();
    assertThat(variable.value()).isEqualTo(clusterVariableRecordValue.getValue());
    assertThat(variable.tenantId()).isEqualTo(clusterVariableRecordValue.getTenantId());
    assertThat(variable.name()).isEqualTo(clusterVariableRecordValue.getName());
    assertThat(variable.scope().toString())
        .isEqualTo(clusterVariableRecordValue.getScope().toString());
  }

  @Test
  public void shouldExportAll() {
    // given
    final var processInstanceRecord = FIXTURES.getProcessInstanceStartedRecord();

    final Record<RecordValue> variableCreated =
        ImmutableRecord.builder()
            .from(RecordFixtures.FACTORY.generateRecord(ValueType.VARIABLE))
            .withIntent(VariableIntent.CREATED)
            .withPosition(2L)
            .withTimestamp(System.currentTimeMillis())
            .build();
    final List<Record<RecordValue>> recordList = List.of(processInstanceRecord, variableCreated);

    // when
    recordList.forEach(record -> exporter.export(record));

    // then
    final var key =
        ((ProcessInstanceRecordValue) processInstanceRecord.getValue()).getProcessInstanceKey();
    final var processInstance = rdbmsService.getProcessInstanceReader().findOne(key);
    assertThat(processInstance).isNotEmpty();
    verifyRootProcessInstanceKey(processInstance.get(), processInstanceRecord);

    final var variable = rdbmsService.getVariableReader().findOne(variableCreated.getKey());
    final VariableRecordValue variableRecordValue =
        (VariableRecordValue) variableCreated.getValue();
    assertThat(variable).isNotNull();
    assertThat(variable.value()).isEqualTo(variableRecordValue.getValue());
  }

  @Test
  public void shouldExportElement() {
    // given
    final var elementRecord = FIXTURES.getElementActivatingRecord();

    // when
    exporter.export(elementRecord);

    // then
    final var key = elementRecord.getKey();
    final var element = rdbmsService.getFlowNodeInstanceReader().findOne(key);
    assertThat(element).isNotEmpty();
    verifyRootProcessInstanceKey(element.get(), elementRecord);

    // given
    final var elementCompleteRecord = FIXTURES.getElementCompletedRecord(key);

    // when
    exporter.export(elementCompleteRecord);

    // then
    final var completedElement = rdbmsService.getFlowNodeInstanceReader().findOne(key);
    assertThat(completedElement).isNotEmpty();
    assertThat(completedElement.get().state()).isEqualTo(FlowNodeState.COMPLETED);
    // Default tree path
    assertThat(completedElement.get().treePath()).isEqualTo("1/2");
    verifyRootProcessInstanceKey(completedElement.get(), elementRecord);
  }

  @Test
  public void shouldExportSequenceFlow() {
    // given
    final var flowTakenRecord = FIXTURES.getSequenceFlowTakenRecord();

    // when
    exporter.export(flowTakenRecord);

    // then
    final var key = flowTakenRecord.getKey();
    final var sequenceFlowQuery =
        SequenceFlowQuery.of(b -> b.filter(f -> f.processInstanceKey(key)));
    final var sequenceFlows = rdbmsService.getSequenceFlowReader().search(sequenceFlowQuery);
    assertThat(sequenceFlows.total()).isEqualTo(1L);
    final var sequenceFlow = sequenceFlows.items().getFirst();
    verifyRootProcessInstanceKey(sequenceFlow, flowTakenRecord);

    // given
    final var flowDeletedRecord =
        FIXTURES.getSequenceFlowDeletedRecord(key, flowTakenRecord.getValue());

    // when
    exporter.export(flowDeletedRecord);

    // then
    final var deletedSequenceFlows = rdbmsService.getSequenceFlowReader().search(sequenceFlowQuery);
    assertThat(deletedSequenceFlows.total()).isEqualTo(0L);
    assertThat(deletedSequenceFlows.items()).isEmpty();
  }

  @Test
  public void shouldExportUserTask() {
    // given
    final var userTaskRecord = FIXTURES.getUserTaskCreatingRecord();

    // when
    exporter.export(userTaskRecord);

    // then
    final UserTaskRecordValue recordValue = (UserTaskRecordValue) userTaskRecord.getValue();
    final var key = recordValue.getUserTaskKey();
    final var userTask = rdbmsService.getUserTaskReader().findOne(key);
    assertThat(userTask).isNotEmpty();
    assertThat(userTask.get().processInstanceKey()).isEqualTo(recordValue.getProcessInstanceKey());
    assertThat(userTask.get().rootProcessInstanceKey())
        .isEqualTo(recordValue.getRootProcessInstanceKey());
  }

  @Test
  public void shouldExportDecisionRequirements() {
    // given
    final var record = FIXTURES.getDecisionRequirementsCreatedRecord();

    // when
    exporter.export(record);

    // then
    final var key =
        ((DecisionRequirementsRecordValue) record.getValue()).getDecisionRequirementsKey();
    final var entity = rdbmsService.getDecisionRequirementsReader().findOne(key);
    assertThat(entity).isNotEmpty();
  }

  @Test
  public void shouldExportDecisionDefinition() {
    // given
    final var decisionDefinitionRecord = FIXTURES.getDecisionDefinitionCreatedRecord();

    // when
    exporter.export(decisionDefinitionRecord);

    // then
    final var key = ((DecisionRecordValue) decisionDefinitionRecord.getValue()).getDecisionKey();
    final var definition = rdbmsService.getDecisionDefinitionReader().findOne(key);
    assertThat(definition).isNotEmpty();
  }

  @Test
  public void shouldExportDecisionEvaluation() {
    // given
    final ImmutableEvaluatedDecisionValue evaluatedDecisionValue =
        ImmutableEvaluatedDecisionValue.builder()
            .withDecisionEvaluationInstanceKey("1234")
            .withDecisionId("decision-id")
            .withDecisionKey(123L)
            .withDecisionType(DecisionDefinitionType.DECISION_TABLE.toString())
            .build();
    final var decisionEvaluationRecord =
        FIXTURES.getDecisionEvaluationEvaluatedRecord(List.of(evaluatedDecisionValue));

    // when
    exporter.export(decisionEvaluationRecord);

    // then
    final var key = evaluatedDecisionValue.getDecisionEvaluationInstanceKey();
    final var decisionInstance = rdbmsService.getDecisionInstanceReader().findOne(key);
    assertThat(decisionInstance).isNotEmpty();
    final var recordValue = (DecisionEvaluationRecordValue) decisionEvaluationRecord.getValue();
    assertThat(decisionInstance.get().processInstanceKey())
        .isEqualTo(recordValue.getProcessInstanceKey());
    assertThat(decisionInstance.get().rootProcessInstanceKey())
        .isEqualTo(recordValue.getRootProcessInstanceKey());
  }

  @Test
  public void shouldExportUpdateAndDeleteUser() {
    // given
    final var userRecord = FIXTURES.getUserRecord(42L, "test", UserIntent.CREATED);
    final var userRecordValue = ((UserRecordValue) userRecord.getValue());

    // when
    exporter.export(userRecord);

    // then
    final var user = rdbmsService.getUserReader().findOne(userRecord.getKey());
    assertThat(user).isNotEmpty();
    assertThat(user.get().userKey()).isEqualTo(userRecordValue.getUserKey());
    assertThat(user.get().username()).isEqualTo(userRecordValue.getUsername());
    assertThat(user.get().name()).isEqualTo(userRecordValue.getName());
    assertThat(user.get().email()).isEqualTo(userRecordValue.getEmail());
    assertThat(user.get().password()).isEqualTo(userRecordValue.getPassword());

    // given
    final var updateUserRecord = FIXTURES.getUserRecord(42L, "test", UserIntent.UPDATED);
    final var updateUserRecordValue = ((UserRecordValue) updateUserRecord.getValue());

    // when
    exporter.export(updateUserRecord);

    // then
    final var updatedUser = rdbmsService.getUserReader().findOne(userRecord.getKey());
    assertThat(updatedUser).isNotEmpty();
    assertThat(updatedUser.get().userKey()).isEqualTo(updateUserRecordValue.getUserKey());
    assertThat(updatedUser.get().username()).isEqualTo(updateUserRecordValue.getUsername());
    assertThat(updatedUser.get().name()).isEqualTo(updateUserRecordValue.getName());
    assertThat(updatedUser.get().email()).isEqualTo(updateUserRecordValue.getEmail());
    assertThat(updatedUser.get().password()).isEqualTo(updateUserRecordValue.getPassword());

    // when
    exporter.export(FIXTURES.getUserRecord(42L, "test", UserIntent.DELETED));

    // then
    final var deletedUser = rdbmsService.getUserReader().findOne(userRecord.getKey());
    assertThat(deletedUser).isEmpty();
  }

  @Test
  public void shouldExportAndUpdateTenant() {
    final var tenantId = "tenant=" + nextKey();
    // given
    final var tenantRecord = FIXTURES.getTenantRecord(42L, tenantId, TenantIntent.CREATED);
    final var tenantRecordValue = ((TenantRecordValue) tenantRecord.getValue());

    // when
    exporter.export(tenantRecord);

    // then
    final var tenant =
        rdbmsService
            .getTenantReader()
            .findOne(((TenantRecordValue) tenantRecord.getValue()).getTenantId());
    assertThat(tenant).isNotEmpty();
    assertThat(tenant.get().key()).isEqualTo(tenantRecord.getKey());
    assertThat(tenant.get().key()).isEqualTo(tenantRecordValue.getTenantKey());
    assertThat(tenant.get().tenantId()).isEqualTo(tenantRecordValue.getTenantId());
    assertThat(tenant.get().name()).isEqualTo(tenantRecordValue.getName());

    // given
    final var updateTenantRecord = FIXTURES.getTenantRecord(42L, tenantId, TenantIntent.UPDATED);
    final var updateTenantRecordValue = ((TenantRecordValue) updateTenantRecord.getValue());

    // when
    exporter.export(updateTenantRecord);

    // then
    final var updatedTenant =
        rdbmsService
            .getTenantReader()
            .findOne(((TenantRecordValue) tenantRecord.getValue()).getTenantId());
    assertThat(updatedTenant).isNotEmpty();
    assertThat(updatedTenant.get().key()).isEqualTo(updateTenantRecordValue.getTenantKey());
    assertThat(updatedTenant.get().tenantId()).isEqualTo(updateTenantRecordValue.getTenantId());
    assertThat(updatedTenant.get().name()).isEqualTo(updateTenantRecordValue.getName());
  }

  @Test
  public void shouldExportUpdateAndDeleteRole() {
    // given
    final var roleId = "roleId";
    final var roleRecord = FIXTURES.getRoleRecord(roleId, RoleIntent.CREATED);
    final var recordValue = (RoleRecordValue) roleRecord.getValue();

    // when
    exporter.export(roleRecord);

    // then
    final var role = rdbmsService.getRoleReader().findOne(recordValue.getRoleId());
    assertThat(role).isNotEmpty();
    assertThat(role.get().roleKey()).isEqualTo(recordValue.getRoleKey());
    assertThat(role.get().roleId()).isEqualTo(recordValue.getRoleId());
    assertThat(role.get().name()).isEqualTo(recordValue.getName());
    assertThat(role.get().description()).isEqualTo(recordValue.getDescription());

    // given
    final var updateRoleRecord = FIXTURES.getRoleRecord(roleId, RoleIntent.UPDATED);
    final var updateRoleRecordValue = ((RoleRecordValue) updateRoleRecord.getValue());

    // when
    exporter.export(updateRoleRecord);

    // then
    final var updatedRole = rdbmsService.getRoleReader().findOne(recordValue.getRoleId());
    assertThat(updatedRole).isNotEmpty();
    assertThat(updatedRole.get().roleKey()).isEqualTo(updateRoleRecordValue.getRoleKey());
    assertThat(updatedRole.get().roleId()).isEqualTo(updateRoleRecordValue.getRoleId());
    assertThat(updatedRole.get().name()).isEqualTo(updateRoleRecordValue.getName());
    assertThat(updatedRole.get().description()).isEqualTo(updateRoleRecordValue.getDescription());

    // when
    exporter.export(FIXTURES.getRoleRecord(roleId, RoleIntent.DELETED));

    // then
    final var deletedRole = rdbmsService.getRoleReader().findOne(recordValue.getRoleId());
    assertThat(deletedRole).isEmpty();
  }

  @Test
  public void shouldExportRoleAndAddAndDeleteMember() {
    // given
    final var roleId = "roleId";
    final var roleRecord = FIXTURES.getRoleRecord(roleId, RoleIntent.CREATED);
    final var recordValue = (RoleRecordValue) roleRecord.getValue();
    final var username = "username";
    final var userRecord = FIXTURES.getUserRecord(1L, username, UserIntent.CREATED);
    exporter.export(userRecord);

    // when
    exporter.export(roleRecord);

    // then
    final var role = rdbmsService.getRoleReader().findOne(recordValue.getRoleId());
    assertThat(role).isNotEmpty();
    assertThat(role.get().roleKey()).isEqualTo(recordValue.getRoleKey());
    assertThat(role.get().roleId()).isEqualTo(recordValue.getRoleId());
    assertThat(role.get().name()).isEqualTo(recordValue.getName());
    assertThat(role.get().description()).isEqualTo(recordValue.getDescription());

    // when
    exporter.export(FIXTURES.getRoleRecord(roleId, RoleIntent.ENTITY_ADDED, username));

    // then
    assertThat(rdbmsService.getRoleReader().findOne(recordValue.getRoleId())).isPresent();
    final var usersWithRole =
        rdbmsService
            .getUserReader()
            .search(
                UserQuery.of(q -> q.filter(new Builder().roleId(recordValue.getRoleId()).build())))
            .items();
    assertThat(usersWithRole).extracting(UserEntity::username).containsExactly(username);

    // when
    exporter.export(FIXTURES.getRoleRecord(roleId, RoleIntent.ENTITY_REMOVED, username));

    // then
    assertThat(rdbmsService.getRoleReader().findOne(recordValue.getRoleId())).isPresent();
    final var usersWithRoleAfterDeletion =
        rdbmsService
            .getUserReader()
            .search(
                UserQuery.of(q -> q.filter(new Builder().roleId(recordValue.getRoleId()).build())))
            .items();
    assertThat(usersWithRoleAfterDeletion).isEmpty();
  }

  @Test
  public void shouldExportUpdateAndDeleteGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupRecord = FIXTURES.getGroupRecord(groupId, GroupIntent.CREATED);
    final var groupRecordValue = ((GroupRecordValue) groupRecord.getValue());

    // when
    exporter.export(groupRecord);

    // then
    final var group =
        rdbmsService
            .getGroupReader()
            .findOne(((GroupRecordValue) groupRecord.getValue()).getGroupId());
    assertThat(group).isNotEmpty();
    assertThat(group.get().groupKey()).isEqualTo(groupRecordValue.getGroupKey());
    assertThat(group.get().groupId()).isEqualTo(groupRecordValue.getGroupId());
    assertThat(group.get().name()).isEqualTo(groupRecordValue.getName());
    assertThat(group.get().description()).isEqualTo(groupRecordValue.getDescription());

    // given
    final var updateGroupRecord = FIXTURES.getGroupRecord(groupId, GroupIntent.UPDATED);
    final var updateGroupRecordValue = ((GroupRecordValue) updateGroupRecord.getValue());

    // when
    exporter.export(updateGroupRecord);

    // then
    final var updatedGroup =
        rdbmsService
            .getGroupReader()
            .findOne(((GroupRecordValue) groupRecord.getValue()).getGroupId());
    assertThat(updatedGroup).isNotEmpty();
    assertThat(updatedGroup.get().groupKey()).isEqualTo(updateGroupRecordValue.getGroupKey());
    assertThat(updatedGroup.get().groupId()).isEqualTo(updateGroupRecordValue.getGroupId());
    assertThat(updatedGroup.get().name()).isEqualTo(updateGroupRecordValue.getName());
    assertThat(updatedGroup.get().description()).isEqualTo(updateGroupRecordValue.getDescription());

    // when
    exporter.export(FIXTURES.getGroupRecord(groupId, GroupIntent.DELETED));

    // then
    final var deletedGroup =
        rdbmsService
            .getGroupReader()
            .findOne(((GroupRecordValue) groupRecord.getValue()).getGroupId());
    assertThat(deletedGroup).isEmpty();
  }

  @Test
  public void shouldExportIncident() {
    // given
    final var processInstanceRecord = FIXTURES.getProcessInstanceStartedRecord();
    final var processInstanceKey =
        ((ProcessInstanceRecordValue) processInstanceRecord.getValue()).getProcessInstanceKey();
    final var rootProcessInstanceKey =
        getProcessInstanceRootProcessInstanceKey(processInstanceRecord);
    exporter.export(processInstanceRecord);
    final var elementRecord = FIXTURES.getElementActivatingRecord(processInstanceKey);
    final var elementInstanceKey = elementRecord.getKey();
    exporter.export(elementRecord);

    // when
    final var incidentKey = 42L;
    final var incidentRecord =
        FIXTURES.getIncidentRecord(
            IncidentIntent.CREATED,
            incidentKey,
            processInstanceKey,
            rootProcessInstanceKey,
            elementInstanceKey);
    exporter.export(incidentRecord);

    // then
    final var element = rdbmsService.getFlowNodeInstanceReader().findOne(elementInstanceKey);
    assertThat(element).isNotEmpty();
    assertThat(element.get().incidentKey()).isEqualTo(incidentKey);
    assertThat(element.get().hasIncident()).isTrue();

    final var processInstance = rdbmsService.getProcessInstanceReader().findOne(processInstanceKey);
    assertThat(processInstance).isNotEmpty();
    assertThat(processInstance.get().hasIncident()).isTrue();

    final var incident = rdbmsService.getIncidentReader().findOne(incidentKey);
    assertThat(incident).isNotEmpty();
    assertThat(incident.get().incidentKey()).isEqualTo(incidentKey);
    assertThat(incident.get().state()).isEqualTo(IncidentState.ACTIVE);
    assertThat(incident.get().processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(incident.get().rootProcessInstanceKey()).isEqualTo(rootProcessInstanceKey);

    // given
    final var incidentResolvedRecord =
        FIXTURES.getIncidentRecord(
            IncidentIntent.RESOLVED,
            incidentKey,
            processInstanceKey,
            rootProcessInstanceKey,
            elementInstanceKey);

    // when
    exporter.export(incidentResolvedRecord);

    // then
    final var element2 = rdbmsService.getFlowNodeInstanceReader().findOne(elementInstanceKey);
    assertThat(element2).isNotEmpty();
    assertThat(element2.get().incidentKey()).isNull();
    assertThat(element2.get().hasIncident()).isFalse();

    final var processInstance2 =
        rdbmsService.getProcessInstanceReader().findOne(processInstanceKey);
    assertThat(processInstance2).isNotEmpty();
    assertThat(processInstance2.get().hasIncident()).isFalse();

    final var incident2 = rdbmsService.getIncidentReader().findOne(incidentKey).orElseThrow();
    assertThat(incident2.state()).isEqualTo(IncidentState.RESOLVED);
    assertThat(incident2.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(incident2.rootProcessInstanceKey()).isEqualTo(rootProcessInstanceKey);
  }

  @Test
  public void shouldExportJobBatchMetrics() {
    // given
    final var startTime = OffsetDateTime.now(UTC).truncatedTo(ChronoUnit.MILLIS);
    final var lastCreatedAt = startTime.plusSeconds(1);
    final var lastFailedAt = startTime.plusSeconds(2);
    final var lastCompletedAt = startTime.plusSeconds(3);
    final var endTime = startTime.plusSeconds(4);
    final var encodedStrings =
        List.of("tenant1", "worker1", "jobType1", "tenant2", "worker2", "jobType2");
    // Create array of StatusMetricsValue sized for all states
    final StatusMetricsValue[] metricsArray = new StatusMetricsValue[3];

    metricsArray[JobMetricsExportState.CREATED.getIndex()] =
        ImmutableStatusMetricsValue.builder()
            .withCount(3)
            .withLastUpdatedAt(lastCreatedAt.toInstant().toEpochMilli())
            .build();
    metricsArray[JobMetricsExportState.FAILED.getIndex()] =
        ImmutableStatusMetricsValue.builder()
            .withCount(5)
            .withLastUpdatedAt(lastFailedAt.toInstant().toEpochMilli())
            .build();
    metricsArray[JobMetricsExportState.COMPLETED.getIndex()] =
        ImmutableStatusMetricsValue.builder()
            .withCount(10)
            .withLastUpdatedAt(lastCompletedAt.toInstant().toEpochMilli())
            .build();

    final var statusMetrics = List.of(metricsArray);

    // Create second set of metrics for the other tenant/worker/jobType
    final StatusMetricsValue[] metricsArray2 = new StatusMetricsValue[3];

    metricsArray2[JobMetricsExportState.CREATED.getIndex()] =
        ImmutableStatusMetricsValue.builder()
            .withCount(7)
            .withLastUpdatedAt(lastCreatedAt.toInstant().toEpochMilli())
            .build();
    metricsArray2[JobMetricsExportState.FAILED.getIndex()] =
        ImmutableStatusMetricsValue.builder()
            .withCount(2)
            .withLastUpdatedAt(lastFailedAt.toInstant().toEpochMilli())
            .build();
    metricsArray2[JobMetricsExportState.COMPLETED.getIndex()] =
        ImmutableStatusMetricsValue.builder()
            .withCount(15)
            .withLastUpdatedAt(lastCompletedAt.toInstant().toEpochMilli())
            .build();

    final var statusMetrics2 = List.of(metricsArray2);

    final var metrics =
        List.of(
            ImmutableJobMetricsValue.builder()
                .withTenantIdIndex(0)
                .withWorkerNameIndex(1)
                .withJobTypeIndex(2)
                .withStatusMetrics(statusMetrics)
                .build(),
            ImmutableJobMetricsValue.builder()
                .withTenantIdIndex(3)
                .withWorkerNameIndex(4)
                .withJobTypeIndex(5)
                .withStatusMetrics(statusMetrics2)
                .build());
    final var jobBatchRecord =
        FIXTURES.getJobMetricsBatchRecord(
            JobMetricsBatchIntent.EXPORTED,
            startTime.toInstant().toEpochMilli(),
            endTime.toInstant().toEpochMilli(),
            encodedStrings,
            metrics,
            false);

    // when
    exporter.export(jobBatchRecord);

    // then
    assertGetGlobalJobStatistics(startTime, endTime, lastCreatedAt, lastFailedAt, lastCompletedAt);
  }

  private void assertGetGlobalJobStatistics(
      final OffsetDateTime startTime,
      final OffsetDateTime endTime,
      final OffsetDateTime lastCreatedAt,
      final OffsetDateTime lastFailedAt,
      final OffsetDateTime lastCompletedAt) {
    final var jobBatchMetrics =
        rdbmsService
            .getJobMetricsBatchDbReader()
            .getGlobalJobStatistics(
                GlobalJobStatisticsQuery.of(
                    b ->
                        b.filter(
                            f -> f.from(startTime.minusSeconds(1)).to(endTime.plusSeconds(1)))),
                ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));
    assertThat(jobBatchMetrics).isNotNull();
    assertThat(jobBatchMetrics.isIncomplete()).isFalse();
    final var createdJobMetrics = jobBatchMetrics.created();
    assertThat(createdJobMetrics.count()).isEqualTo(10); // 3 + 7
    assertThat(createdJobMetrics.lastUpdatedAt()).isEqualTo(lastCreatedAt);
    final var failedJobMetrics = jobBatchMetrics.failed();
    assertThat(failedJobMetrics.count()).isEqualTo(7); // 5 + 2
    assertThat(failedJobMetrics.lastUpdatedAt()).isEqualTo(lastFailedAt);
    final var completedJobMetrics = jobBatchMetrics.completed();
    assertThat(completedJobMetrics.count()).isEqualTo(25); // 10 + 15
    assertThat(completedJobMetrics.lastUpdatedAt()).isEqualTo(lastCompletedAt);
  }

  @Test
  public void shouldExportForm() {
    // given
    final var formCreatedRecord = FIXTURES.getFormCreatedRecord();

    // when
    exporter.export(formCreatedRecord);

    // then
    final var formKey = ((Form) formCreatedRecord.getValue()).getFormKey();
    final var formEntity = rdbmsService.getFormReader().findOne(formKey);
    assertThat(formEntity).isNotEmpty();
  }

  @Test
  public void shouldExportMessageSubscription() {
    // given
    final var messageSubscriptionRecord =
        ImmutableRecord.<ProcessMessageSubscriptionRecordValue>builder()
            .from(RecordFixtures.FACTORY.generateRecord(ValueType.PROCESS_MESSAGE_SUBSCRIPTION))
            .withIntent(ProcessMessageSubscriptionIntent.CREATED)
            .withTimestamp(System.currentTimeMillis())
            .build();

    // when
    exporter.export(messageSubscriptionRecord);

    // then
    final var messageSubscription =
        rdbmsService.getMessageSubscriptionReader().findOne(messageSubscriptionRecord.getKey());
    assertThat(messageSubscription).isNotEmpty();
    final var recordValue = messageSubscriptionRecord.getValue();
    assertThat(messageSubscription.get().processInstanceKey())
        .isEqualTo(recordValue.getProcessInstanceKey());
    assertThat(messageSubscription.get().rootProcessInstanceKey())
        .isEqualTo(recordValue.getRootProcessInstanceKey());
  }

  @Test
  public void shouldUpdateDeletedMessageSubscription() {
    // given
    final var messageSubscriptionRecord =
        ImmutableRecord.builder()
            .from(RecordFixtures.FACTORY.generateRecord(ValueType.PROCESS_MESSAGE_SUBSCRIPTION))
            .withIntent(ProcessMessageSubscriptionIntent.CREATED)
            .withTimestamp(System.currentTimeMillis())
            .build();

    exporter.export(messageSubscriptionRecord);

    // when
    exporter.export(
        ImmutableRecord.builder()
            .from(messageSubscriptionRecord)
            .withIntent(ProcessMessageSubscriptionIntent.DELETED)
            .withTimestamp(System.currentTimeMillis())
            .build());

    // then
    final var messageSubscription =
        rdbmsService.getMessageSubscriptionReader().findOne(messageSubscriptionRecord.getKey());
    assertThat(messageSubscription).isPresent();
    assertThat(messageSubscription.get().messageSubscriptionState())
        .isEqualTo(MessageSubscriptionState.DELETED);
  }

  @Test
  public void shouldExportCorrelatedMessageSubscription() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> correlatedMessageSubscriptionRecord =
        ImmutableRecord.<ProcessMessageSubscriptionRecordValue>builder()
            .from(RecordFixtures.FACTORY.generateRecord(ValueType.PROCESS_MESSAGE_SUBSCRIPTION))
            .withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
            .withTimestamp(System.currentTimeMillis())
            .build();

    // when
    exporter.export(correlatedMessageSubscriptionRecord);

    // then
    final var recordValue = correlatedMessageSubscriptionRecord.getValue();
    final var correlatedMessageSubscription =
        rdbmsService
            .getCorrelatedMessageSubscriptionReader()
            .findOne(recordValue.getMessageKey(), correlatedMessageSubscriptionRecord.getKey());
    assertThat(correlatedMessageSubscription).isNotEmpty();
    assertThat(correlatedMessageSubscription.get().processInstanceKey())
        .isEqualTo(recordValue.getProcessInstanceKey());
    assertThat(correlatedMessageSubscription.get().rootProcessInstanceKey())
        .isEqualTo(recordValue.getRootProcessInstanceKey());
  }

  @Test
  public void shouldExportCorrelatedMessageSubscriptionFromStartEvent() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> messageStartEventSubRecord =
        ImmutableRecord.<MessageStartEventSubscriptionRecordValue>builder()
            .from(RecordFixtures.FACTORY.generateRecord(ValueType.MESSAGE_START_EVENT_SUBSCRIPTION))
            .withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
            .withPosition(2L)
            .withTimestamp(System.currentTimeMillis())
            .build();

    // when
    exporter.export(messageStartEventSubRecord);

    // then
    final var recordValue = messageStartEventSubRecord.getValue();
    final var correlatedMessageSubscription =
        rdbmsService
            .getCorrelatedMessageSubscriptionReader()
            .findOne(recordValue.getMessageKey(), messageStartEventSubRecord.getKey());
    assertThat(correlatedMessageSubscription).isNotEmpty();
    final var processInstanceKey = recordValue.getProcessInstanceKey();
    assertThat(correlatedMessageSubscription.get().processInstanceKey())
        .isEqualTo(processInstanceKey);
    assertThat(correlatedMessageSubscription.get().rootProcessInstanceKey())
        .isEqualTo(processInstanceKey);
  }

  @Test
  public void shouldExportCreatedAndDeletedMapping() {
    // given
    final var mappingRuleCreatedRecord = FIXTURES.getMappingRuleRecord(MappingRuleIntent.CREATED);

    // when
    exporter.export(mappingRuleCreatedRecord);

    // then
    final var mappingRuleId =
        ((MappingRuleRecordValue) mappingRuleCreatedRecord.getValue()).getMappingRuleId();
    final var mappingRule = rdbmsService.getMappingRuleReader().findOne(mappingRuleId);
    assertThat(mappingRule).isNotEmpty();

    // given
    final var mappingDeletedRecord =
        mappingRuleCreatedRecord
            .withIntent(MappingRuleIntent.DELETED)
            .withPosition(FIXTURES.nextPosition());

    // when
    exporter.export(mappingDeletedRecord);

    // then
    final var deletedMapping = rdbmsService.getMappingRuleReader().findOne(mappingRuleId);
    assertThat(deletedMapping).isEmpty();
  }

  @Test
  public void shouldExportAndUpdateAuthorization() {
    // given
    final var authorizationRecord =
        FIXTURES.getAuthorizationRecord(
            AuthorizationIntent.CREATED,
            1337L,
            "foo",
            AuthorizationOwnerType.USER,
            AuthorizationResourceType.PROCESS_DEFINITION,
            "resource1",
            Set.of(PermissionType.READ, PermissionType.CREATE));

    // when
    exporter.export(authorizationRecord);

    // then
    final var recordValue = (AuthorizationRecordValue) authorizationRecord.getValue();
    final var authorization =
        rdbmsService
            .getAuthorizationReader()
            .findOne(
                recordValue.getOwnerId(),
                recordValue.getOwnerType().name(),
                recordValue.getResourceType().name())
            .orElse(null);
    assertThat(authorization).isNotNull();

    // given
    final var authorizationUpdatedRecord =
        FIXTURES.getAuthorizationRecord(
            AuthorizationIntent.UPDATED,
            1337L,
            "foo",
            AuthorizationOwnerType.USER,
            AuthorizationResourceType.PROCESS_DEFINITION,
            "resource1",
            Set.of(PermissionType.UPDATE, PermissionType.DELETE));

    // when
    exporter.export(authorizationUpdatedRecord);

    // then
    final var updatedAuthorization =
        rdbmsService
            .getAuthorizationReader()
            .findOne(
                recordValue.getOwnerId(),
                recordValue.getOwnerType().name(),
                recordValue.getResourceType().name())
            .orElse(null);

    assertThat(updatedAuthorization).isNotNull();
    assertThat(updatedAuthorization.permissionTypes()).hasSize(2);
    assertThat(updatedAuthorization.permissionTypes())
        .containsExactlyInAnyOrder(PermissionType.UPDATE, PermissionType.DELETE);
  }

  @Test
  public void shouldExportAndDeleteAuthorization() {
    // given
    final var authorizationRecord =
        FIXTURES.getAuthorizationRecord(
            AuthorizationIntent.CREATED,
            1337L,
            "foo",
            AuthorizationOwnerType.USER,
            AuthorizationResourceType.PROCESS_DEFINITION,
            "resource1",
            Set.of(PermissionType.READ, PermissionType.CREATE));

    // when
    exporter.export(authorizationRecord);

    // then
    final var recordValue = (AuthorizationRecordValue) authorizationRecord.getValue();
    final var authorization =
        rdbmsService
            .getAuthorizationReader()
            .findOne(
                recordValue.getOwnerId(),
                recordValue.getOwnerType().name(),
                recordValue.getResourceType().name())
            .orElse(null);
    assertThat(authorization).isNotNull();

    // given
    final var authorizationDeletedRecord =
        FIXTURES.getAuthorizationRecord(
            AuthorizationIntent.DELETED,
            recordValue.getAuthorizationKey(),
            recordValue.getOwnerId(),
            recordValue.getOwnerType(),
            recordValue.getResourceType(),
            recordValue.getResourceId(),
            recordValue.getPermissionTypes());

    // when
    exporter.export(authorizationDeletedRecord);

    // then
    final var deletedAuthorization =
        rdbmsService
            .getAuthorizationReader()
            .findOne(
                recordValue.getOwnerId(),
                recordValue.getOwnerType().name(),
                recordValue.getResourceType().name())
            .orElse(null);
    assertThat(deletedAuthorization).isNull();
  }

  @Test
  public void shouldExportBatchOperationCreatedRecord() {
    // given
    final Record<BatchOperationCreationRecordValue> record =
        RecordFixtures.FACTORY.generateRecord(
            ValueType.BATCH_OPERATION_CREATION,
            r ->
                r.withIntent(BatchOperationIntent.CREATED)
                    .withTimestamp(System.currentTimeMillis()));
    final long batchOperationKey = record.getKey();
    final var batchOperationCreationRecord =
        ImmutableRecord.<BatchOperationCreationRecordValue>builder()
            .from(record)
            .withValue(
                ImmutableBatchOperationCreationRecordValue.builder()
                    .from(record.getValue())
                    .withBatchOperationKey(batchOperationKey)
                    .withBatchOperationType(
                        io.camunda.zeebe.protocol.record.value.BatchOperationType
                            .MIGRATE_PROCESS_INSTANCE)
                    .build())
            .build();

    // when
    exporter.export(batchOperationCreationRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey));
    assertThat(batchOperation)
        .hasValueSatisfying(
            entity -> {
              assertThat(entity.state()).isEqualTo(BatchOperationState.CREATED);
              assertThat(entity.startDate()).isNull();
              assertThat(entity.operationType())
                  .isEqualTo(BatchOperationType.MIGRATE_PROCESS_INSTANCE);
              assertThat(entity.batchOperationKey()).isEqualTo(String.valueOf(batchOperationKey));
            });
  }

  @Test
  public void shouldExportBatchOperationActivatedRecord() {
    // given
    final var batchOperationKey =
        givenBatchOperationCreatedAndActivated(
            io.camunda.zeebe.protocol.record.value.BatchOperationType.MIGRATE_PROCESS_INSTANCE);

    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey));
    assertThat(batchOperation)
        .hasValueSatisfying(
            entity -> {
              assertThat(entity.operationType())
                  .isEqualTo(BatchOperationType.MIGRATE_PROCESS_INSTANCE);
              assertThat(entity.state()).isEqualTo(BatchOperationState.ACTIVE);
              assertThat(entity.startDate()).isNotNull();
              assertThat(entity.operationsTotalCount()).isEqualTo(0L);
              assertThat(entity.operationsCompletedCount()).isEqualTo(0L);
              assertThat(entity.operationsFailedCount()).isEqualTo(0L);
            });
  }

  @Test
  public void shouldExportBatchOperationChunkRecord() {
    final var batchOperationKey =
        givenBatchOperationCreatedAndActivated(
            io.camunda.zeebe.protocol.record.value.BatchOperationType.MODIFY_PROCESS_INSTANCE);

    final var itemKey = 9043L;
    final var processInstanceKey = 9044L;
    final var rootProcessInstanceKey = 9033L;

    final Record<BatchOperationChunkRecordValue> record =
        RecordFixtures.FACTORY.generateRecord(ValueType.BATCH_OPERATION_CHUNK);
    final var batchOperationChunkedRecord =
        ImmutableRecord.<BatchOperationChunkRecordValue>builder()
            .from(record)
            .withIntent(BatchOperationChunkIntent.CREATE)
            .withTimestamp(System.currentTimeMillis())
            .withBatchOperationReference(batchOperationKey)
            .withValue(
                ImmutableBatchOperationChunkRecordValue.builder()
                    .from(record.getValue())
                    .withBatchOperationKey(batchOperationKey)
                    .withItems(
                        List.of(
                            ImmutableBatchOperationItemValue.builder()
                                .withItemKey(itemKey)
                                .withProcessInstanceKey(processInstanceKey)
                                .withRootProcessInstanceKey(rootProcessInstanceKey)
                                .build()))
                    .build())
            .build();
    // when
    exporter.export(batchOperationChunkedRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey));
    assertThat(batchOperation).isNotEmpty();
    assertThat(batchOperation.get().operationType())
        .isEqualTo(BatchOperationType.MODIFY_PROCESS_INSTANCE);
    assertThat(batchOperation.get().state()).isEqualTo(BatchOperationState.ACTIVE);
    assertThat(batchOperation.get().operationsTotalCount()).isEqualTo(1L);
    assertThat(batchOperation.get().operationsCompletedCount()).isEqualTo(0L);
    assertThat(batchOperation.get().operationsFailedCount()).isEqualTo(0L);

    final var results = searchBatchOperationItems(itemKey);
    final var items = results.items();
    assertThat(items).hasSize(1);
    final var item = items.getFirst();
    assertThat(item.itemKey()).isEqualTo(itemKey);
    assertThat(item.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(item.rootProcessInstanceKey()).isEqualTo(rootProcessInstanceKey);
    assertThat(item.operationType()).isEqualTo(BatchOperationType.MODIFY_PROCESS_INSTANCE);
    assertThat(item.state()).isEqualTo(BatchOperationItemState.ACTIVE);
  }

  @Test
  public void
      shouldUpsertBatchOperationItemForProcessInstanceHistoryDeletionThatRelatesToBatchOperation() {
    // given
    final var batchOperationKey =
        givenBatchOperationCreatedAndActivated(
            io.camunda.zeebe.protocol.record.value.BatchOperationType.DELETE_PROCESS_INSTANCE);

    final var resourceKey = 1153L;
    final var deletionRecord =
        withBatchOperationReference(
            FIXTURES.getHistoryDeletionRecord(
                HistoryDeletionIntent.DELETED, resourceKey, HistoryDeletionType.PROCESS_INSTANCE),
            batchOperationKey);

    // when
    exporter.export(deletionRecord);

    // then
    final var results = searchBatchOperationItems(resourceKey);
    final var items = results.items();
    assertThat(items).hasSize(1);
    final var item = items.getFirst();
    assertThat(item.itemKey()).isEqualTo(resourceKey);
    assertThat(item.processInstanceKey()).isEqualTo(resourceKey);
    assertThat(item.rootProcessInstanceKey()).isNull();
    assertThat(item.operationType()).isEqualTo(BatchOperationType.DELETE_PROCESS_INSTANCE);
    assertThat(item.state()).isEqualTo(BatchOperationItemState.COMPLETED);
  }

  @Test
  public void
      shouldUpsertBatchOperationItemForDecisionInstanceHistoryDeletionThatRelatesToBatchOperation() {
    // given
    final var batchOperationKey =
        givenBatchOperationCreatedAndActivated(
            io.camunda.zeebe.protocol.record.value.BatchOperationType.DELETE_DECISION_INSTANCE);

    final var resourceKey = 1154L;
    final var deletionRecord =
        withBatchOperationReference(
            FIXTURES.getHistoryDeletionRecord(
                HistoryDeletionIntent.DELETED, resourceKey, HistoryDeletionType.DECISION_INSTANCE),
            batchOperationKey);

    // when
    exporter.export(deletionRecord);

    // then
    final var results = searchBatchOperationItems(resourceKey);
    final var items = results.items();
    assertThat(items).hasSize(1);
    final var item = items.getFirst();
    assertThat(item.itemKey()).isEqualTo(resourceKey);
    assertThat(item.processInstanceKey()).isNull();
    assertThat(item.rootProcessInstanceKey()).isNull();
    assertThat(item.operationType()).isEqualTo(BatchOperationType.DELETE_DECISION_INSTANCE);
    assertThat(item.state()).isEqualTo(BatchOperationItemState.COMPLETED);
  }

  @Test
  public void shouldUpsertBatchOperationItemForResolvedIncidentThatRelatesToBatchOperation() {
    // given
    final var batchOperationKey =
        givenBatchOperationCreatedAndActivated(
            io.camunda.zeebe.protocol.record.value.BatchOperationType.RESOLVE_INCIDENT);

    final var incidentKey = 1142L;
    final var processInstanceKey = 1151L;
    final var rootProcessInstanceKey = 1162L;
    final var elementInstanceKey = 1173L;
    final var incidentRecord =
        withBatchOperationReference(
            FIXTURES.getIncidentRecord(
                IncidentIntent.RESOLVED,
                incidentKey,
                processInstanceKey,
                rootProcessInstanceKey,
                elementInstanceKey),
            batchOperationKey);

    // when
    exporter.export(incidentRecord);

    // then
    final var results = searchBatchOperationItems(incidentKey);
    final var items = results.items();
    assertThat(items).hasSize(1);
    final var item = items.getFirst();
    assertThat(item.itemKey()).isEqualTo(incidentKey);
    assertThat(item.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(item.rootProcessInstanceKey()).isEqualTo(rootProcessInstanceKey);
    assertThat(item.operationType()).isEqualTo(BatchOperationType.RESOLVE_INCIDENT);
    assertThat(item.state()).isEqualTo(BatchOperationItemState.COMPLETED);
  }

  @Test
  public void
      shouldNotUpsertBatchOperationItemForResolvedIncidentThatDoesNotRelateBatchOperation() {
    // given
    final var incidentKey = 1042L;
    final var processInstanceKey = 1051L;
    final var rootProcessInstanceKey = 1062L;
    final var elementInstanceKey = 1073L;
    final var incidentRecord =
        FIXTURES.getIncidentRecord(
            IncidentIntent.RESOLVED,
            incidentKey,
            processInstanceKey,
            rootProcessInstanceKey,
            elementInstanceKey);

    // when
    exporter.export(incidentRecord);

    // then
    final var results = searchBatchOperationItems(incidentKey);
    final var items = results.items();
    assertThat(items).isEmpty();
  }

  @Test
  public void shouldExportJob() {
    // given
    final Record<RecordValue> jobCreatedRecord =
        ImmutableRecord.builder()
            .from(RecordFixtures.FACTORY.generateRecord(ValueType.JOB))
            .withIntent(JobIntent.CREATED)
            .withTimestamp(System.currentTimeMillis())
            .build();

    // when
    exporter.export(jobCreatedRecord);

    // then
    final var job = rdbmsService.getJobReader().findOne(jobCreatedRecord.getKey());
    assertThat(job).isNotNull();
    assertThat(job.get().rootProcessInstanceKey())
        .isEqualTo(((JobRecordValue) jobCreatedRecord.getValue()).getRootProcessInstanceKey());
  }

  @Test
  public void shouldWriteToAuditLog() {
    // given
    final var processInstanceCreationRecord =
        ImmutableRecord.<ProcessInstanceCreationRecordValue>builder()
            .from(RecordFixtures.FACTORY.generateRecord(ValueType.PROCESS_INSTANCE_CREATION))
            .withRecordType(RecordType.EVENT)
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .withPosition(1L)
            .withPartitionId(1)
            .withTimestamp(System.currentTimeMillis())
            .build();

    // when
    exporter.export(processInstanceCreationRecord);

    // then
    final var recordValue = processInstanceCreationRecord.getValue();
    final var results =
        rdbmsService
            .getAuditLogReader()
            .search(
                AuditLogQuery.of(
                    b ->
                        b.filter(f -> f.processInstanceKeys(recordValue.getProcessInstanceKey()))));
    final var items = results.items();
    assertThat(items).hasSize(1);
    final var auditLog = items.getFirst();
    assertThat(auditLog.processInstanceKey()).isEqualTo(recordValue.getProcessInstanceKey());
    assertThat(auditLog.rootProcessInstanceKey())
        .isEqualTo(recordValue.getRootProcessInstanceKey());
    assertThat(auditLog.entityType()).isEqualTo(AuditLogEntityType.PROCESS_INSTANCE);
    assertThat(auditLog.operationType()).isEqualTo(AuditLogOperationType.CREATE);
    assertThat(auditLog.result()).isEqualTo(AuditLogOperationResult.SUCCESS);
    assertThat(auditLog.category()).isEqualTo(AuditLogOperationCategory.DEPLOYED_RESOURCES);
  }

  private static void verifyRootProcessInstanceKey(
      final ProcessInstanceEntity processInstance,
      final ImmutableRecord<RecordValue> processInstanceRecord) {
    assertThat(processInstance.rootProcessInstanceKey())
        .isEqualTo(getProcessInstanceRootProcessInstanceKey(processInstanceRecord));
  }

  private static void verifyRootProcessInstanceKey(
      final FlowNodeInstanceEntity flowNodeInstance,
      final ImmutableRecord<RecordValue> processInstanceRecord) {
    assertThat(flowNodeInstance.rootProcessInstanceKey())
        .isEqualTo(getProcessInstanceRootProcessInstanceKey(processInstanceRecord));
  }

  private void verifyRootProcessInstanceKey(
      final SequenceFlowEntity sequenceFlow,
      final ImmutableRecord<RecordValue> processInstanceRecord) {
    assertThat(sequenceFlow.rootProcessInstanceKey())
        .isEqualTo(getProcessInstanceRootProcessInstanceKey(processInstanceRecord));
  }

  private static long getProcessInstanceRootProcessInstanceKey(final Record<RecordValue> record) {
    return ((ProcessInstanceRecordValue) record.getValue()).getRootProcessInstanceKey();
  }

  private long givenBatchOperationCreatedAndActivated(
      final io.camunda.zeebe.protocol.record.value.BatchOperationType batchOperationType) {
    final Record<BatchOperationCreationRecordValue> record =
        RecordFixtures.FACTORY.generateRecord(ValueType.BATCH_OPERATION_CREATION);
    final var batchOperationCreationRecord =
        ImmutableRecord.<BatchOperationCreationRecordValue>builder()
            .from(record)
            .withIntent(BatchOperationIntent.CREATED)
            .withTimestamp(System.currentTimeMillis())
            .withValue(
                ImmutableBatchOperationCreationRecordValue.builder()
                    .from(record.getValue())
                    .withBatchOperationKey(record.getKey())
                    .withBatchOperationType(batchOperationType)
                    .build())
            .build();
    final long batchOperationKey = batchOperationCreationRecord.getKey();
    final var batchOperationInitializationRecord =
        FIXTURES.getBatchOperationInitializedRecord(batchOperationKey);

    exporter.export(batchOperationCreationRecord);
    exporter.export(batchOperationInitializationRecord);

    return batchOperationKey;
  }

  private <T extends RecordValue> Record<T> withBatchOperationReference(
      final Record<T> record, final long batchOperationKey) {
    return ImmutableRecord.<T>builder()
        .from(record)
        .withBatchOperationReference(batchOperationKey)
        .build();
  }

  private SearchQueryResult<BatchOperationItemEntity> searchBatchOperationItems(
      final long itemKey) {
    return rdbmsService
        .getBatchOperationItemReader()
        .search(BatchOperationItemQuery.of(b -> b.filter(f -> f.itemKeys(itemKey))));
  }
}
