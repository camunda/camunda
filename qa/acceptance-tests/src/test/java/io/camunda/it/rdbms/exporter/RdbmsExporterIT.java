/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.exporter;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getAuthorizationRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getDecisionDefinitionCreatedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getDecisionRequirementsCreatedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getFlowNodeActivatingRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getFlowNodeCompletedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getFormCreatedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getGroupRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getIncidentRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getMappingRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getProcessDefinitionCreatedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getProcessInstanceCompletedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getProcessInstanceStartedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getRoleRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getTenantRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getUserRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getUserTaskCreatedRecord;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.exporter.rdbms.RdbmsExporterWrapper;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
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
      "camunda.database.type=rdbms",
      "zeebe.broker.exporters.rdbms.args.maxQueueSize=0",
      "camunda.database.index-prefix=C8_"
    })
class RdbmsExporterIT {

  private final ExporterTestController controller = new ExporterTestController();

  @Autowired private RdbmsService rdbmsService;

  private RdbmsExporterWrapper exporter;

  @BeforeEach
  void setUp() {
    exporter = new RdbmsExporterWrapper(rdbmsService);
    exporter.configure(
        new ExporterContext(
            null,
            new ExporterConfiguration("foo", Map.of("maxQueueSize", 0)),
            1,
            Mockito.mock(MeterRegistry.class, Mockito.RETURNS_DEEP_STUBS),
            null));
    exporter.open(controller);
  }

  @Test
  public void shouldExportProcessInstance() {
    // given
    final var processInstanceRecord = getProcessInstanceStartedRecord(1L);

    // when
    exporter.export(processInstanceRecord);

    // then
    final var key =
        ((ProcessInstanceRecordValue) processInstanceRecord.getValue()).getProcessInstanceKey();
    final var processInstance = rdbmsService.getProcessInstanceReader().findOne(key);
    assertThat(processInstance).isNotNull();

    // given
    final var processInstanceCompletedRecord = getProcessInstanceCompletedRecord(1L, key);

    // when
    exporter.export(processInstanceCompletedRecord);

    // then
    final var completedProcessInstance = rdbmsService.getProcessInstanceReader().findOne(key);
    assertThat(completedProcessInstance).isNotEmpty();
    assertThat(completedProcessInstance.get().state()).isEqualTo(ProcessInstanceState.COMPLETED);
  }

  @Test
  public void shouldExportProcessDefinition() {
    // given
    final var processDefinitionRecord = getProcessDefinitionCreatedRecord(1L);

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
  }

  @Test
  public void shouldExportAll() {
    // given
    final var processInstanceRecord = getProcessInstanceStartedRecord(1L);

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
    assertThat(processInstance).isNotNull();

    final var variable = rdbmsService.getVariableReader().findOne(variableCreated.getKey());
    final VariableRecordValue variableRecordValue =
        (VariableRecordValue) variableCreated.getValue();
    assertThat(variable).isNotNull();
    assertThat(variable.value()).isEqualTo(variableRecordValue.getValue());
  }

  @Test
  public void shouldExportFlowNode() {
    // given
    final var flowNodeRecord = getFlowNodeActivatingRecord(1L);

    // when
    exporter.export(flowNodeRecord);

    // then
    final var key = flowNodeRecord.getKey();
    final var flowNode = rdbmsService.getFlowNodeInstanceReader().findOne(key);
    assertThat(flowNode).isNotEmpty();

    // given
    final var flowNodeCompleteRecord = getFlowNodeCompletedRecord(1L, key);

    // when
    exporter.export(flowNodeCompleteRecord);

    // then
    final var completedFlowNode = rdbmsService.getFlowNodeInstanceReader().findOne(key);
    assertThat(completedFlowNode).isNotEmpty();
    assertThat(completedFlowNode.get().state()).isEqualTo(FlowNodeState.COMPLETED);
    // Default tree path
    assertThat(completedFlowNode.get().treePath()).isEqualTo("1/2");
  }

  @Test
  public void shouldExportUserTask() {
    // given
    final var userTaskRecord = getUserTaskCreatedRecord(1L);

    // when
    exporter.export(userTaskRecord);

    // then
    final var key = ((UserTaskRecordValue) userTaskRecord.getValue()).getUserTaskKey();
    final var userTask = rdbmsService.getUserTaskReader().findOne(key);
    assertThat(userTask).isNotNull();
  }

  @Test
  public void shouldExportDecisionRequirements() {
    // given
    final var record = getDecisionRequirementsCreatedRecord(1L);

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
    final var decisionDefinitionRecord = getDecisionDefinitionCreatedRecord(1L);

    // when
    exporter.export(decisionDefinitionRecord);

    // then
    final var key = ((DecisionRecordValue) decisionDefinitionRecord.getValue()).getDecisionKey();
    final var definition = rdbmsService.getDecisionDefinitionReader().findOne(key);
    assertThat(definition).isNotEmpty();
  }

  @Test
  public void shouldExportUpdateAndDeleteUser() {
    // given
    final var userRecord = getUserRecord(42L, "test", UserIntent.CREATED);
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
    final var updateUserRecord = getUserRecord(42L, "test", UserIntent.UPDATED);
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
    exporter.export(getUserRecord(42L, "test", UserIntent.DELETED));

    // then
    final var deletedUser = rdbmsService.getUserReader().findOne(userRecord.getKey());
    assertThat(deletedUser).isEmpty();
  }

  @Test
  public void shouldExportAndUpdateTenant() {
    final var tenantId = "tenant=" + nextKey();
    // given
    final var tenantRecord = getTenantRecord(42L, tenantId, TenantIntent.CREATED);
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
    final var updateTenantRecord = getTenantRecord(42L, tenantId, TenantIntent.UPDATED);
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
    final var roleRecord = getRoleRecord(42L, RoleIntent.CREATED);
    final var roleRecordValue = ((RoleRecordValue) roleRecord.getValue());

    // when
    exporter.export(roleRecord);

    // then
    final var role = rdbmsService.getRoleReader().findOne(roleRecord.getKey());
    assertThat(role).isNotEmpty();
    assertThat(role.get().roleKey()).isEqualTo(roleRecordValue.getRoleKey());
    assertThat(role.get().name()).isEqualTo(roleRecordValue.getName());

    // given
    final var updateRoleRecord = getRoleRecord(42L, RoleIntent.UPDATED);
    final var updateRoleRecordValue = ((RoleRecordValue) updateRoleRecord.getValue());

    // when
    exporter.export(updateRoleRecord);

    // then
    final var updatedRole = rdbmsService.getRoleReader().findOne(roleRecord.getKey());
    assertThat(updatedRole).isNotEmpty();
    assertThat(updatedRole.get().roleKey()).isEqualTo(updateRoleRecordValue.getRoleKey());
    assertThat(updatedRole.get().name()).isEqualTo(updateRoleRecordValue.getName());

    // when
    exporter.export(getRoleRecord(42L, RoleIntent.DELETED));

    // then
    final var deletedRole = rdbmsService.getRoleReader().findOne(roleRecord.getKey());
    assertThat(deletedRole).isEmpty();
  }

  @Test
  public void shouldExportRoleAndAddAndDeleteMember() {
    // given
    final var roleRecord = getRoleRecord(42L, RoleIntent.CREATED);
    final var roleRecordValue = ((RoleRecordValue) roleRecord.getValue());

    // when
    exporter.export(roleRecord);

    // then
    final var role = rdbmsService.getRoleReader().findOne(roleRecord.getKey());
    assertThat(role).isNotEmpty();
    assertThat(role.get().roleKey()).isEqualTo(roleRecordValue.getRoleKey());
    assertThat(role.get().name()).isEqualTo(roleRecordValue.getName());

    // when
    exporter.export(getRoleRecord(42L, RoleIntent.ENTITY_ADDED, 1337L));

    // then
    final var updatedRole = rdbmsService.getRoleReader().findOne(roleRecord.getKey()).orElseThrow();
    assertThat(updatedRole.assignedMemberKeys()).containsExactly(1337L);

    // when
    exporter.export(getRoleRecord(42L, RoleIntent.ENTITY_REMOVED, 1337L));

    // then
    final var deletedRole = rdbmsService.getRoleReader().findOne(roleRecord.getKey()).orElseThrow();
    assertThat(deletedRole.assignedMemberKeys()).isEmpty();
  }

  @Test
  public void shouldExportUpdateAndDeleteGroup() {
    // given
    final var groupRecord = getGroupRecord(42L, GroupIntent.CREATED);
    final var groupRecordValue = ((GroupRecordValue) groupRecord.getValue());

    // when
    exporter.export(groupRecord);

    // then
    final var group = rdbmsService.getGroupReader().findOne(groupRecord.getKey());
    assertThat(group).isNotEmpty();
    assertThat(group.get().groupKey()).isEqualTo(groupRecordValue.getGroupKey());
    assertThat(group.get().name()).isEqualTo(groupRecordValue.getName());

    // given
    final var updateGroupRecord = getGroupRecord(42L, GroupIntent.UPDATED);
    final var updateGroupRecordValue = ((GroupRecordValue) updateGroupRecord.getValue());

    // when
    exporter.export(updateGroupRecord);

    // then
    final var updatedGroup = rdbmsService.getGroupReader().findOne(groupRecord.getKey());
    assertThat(updatedGroup).isNotEmpty();
    assertThat(updatedGroup.get().groupKey()).isEqualTo(updateGroupRecordValue.getGroupKey());
    assertThat(updatedGroup.get().name()).isEqualTo(updateGroupRecordValue.getName());

    // when
    exporter.export(getGroupRecord(42L, GroupIntent.DELETED));

    // then
    final var deletedGroup = rdbmsService.getGroupReader().findOne(groupRecord.getKey());
    assertThat(deletedGroup).isEmpty();
  }

  @Test
  public void shouldExportGroupAndAddAndDeleteMember() {
    // given
    final var groupRecord = getGroupRecord(43L, GroupIntent.CREATED);
    final var groupRecordValue = ((GroupRecordValue) groupRecord.getValue());

    // when
    exporter.export(groupRecord);

    // then
    final var group = rdbmsService.getGroupReader().findOne(groupRecord.getKey());
    assertThat(group).isNotEmpty();
    assertThat(group.get().groupKey()).isEqualTo(groupRecordValue.getGroupKey());
    assertThat(group.get().name()).isEqualTo(groupRecordValue.getName());

    // when
    exporter.export(getGroupRecord(43L, GroupIntent.ENTITY_ADDED, 1337L));

    // then
    final var updatedGroup =
        rdbmsService.getGroupReader().findOne(groupRecord.getKey()).orElseThrow();
    assertThat(updatedGroup.assignedMemberKeys()).containsExactly(1337L);

    // when
    exporter.export(getGroupRecord(43L, GroupIntent.ENTITY_REMOVED, 1337L));

    // then
    final var deletedGroup =
        rdbmsService.getGroupReader().findOne(groupRecord.getKey()).orElseThrow();
    assertThat(deletedGroup.assignedMemberKeys()).isEmpty();
  }

  @Test
  public void shouldExportIncident() {
    // given
    final var processInstanceRecord = getProcessInstanceStartedRecord(1L);
    final var processInstanceKey =
        ((ProcessInstanceRecordValue) processInstanceRecord.getValue()).getProcessInstanceKey();
    exporter.export(processInstanceRecord);
    final var flowNodeRecord = getFlowNodeActivatingRecord(1L, processInstanceKey);
    final var flowNodeInstanceKey = flowNodeRecord.getKey();
    exporter.export(flowNodeRecord);

    // when
    final var incidentKey = 42L;
    final var incidentRecord =
        getIncidentRecord(
            IncidentIntent.CREATED, incidentKey, processInstanceKey, flowNodeInstanceKey);
    exporter.export(incidentRecord);

    // then
    final var flowNode = rdbmsService.getFlowNodeInstanceReader().findOne(flowNodeInstanceKey);
    assertThat(flowNode).isNotEmpty();
    assertThat(flowNode.get().incidentKey()).isEqualTo(incidentKey);
    assertThat(flowNode.get().hasIncident()).isTrue();
    final var processInstance = rdbmsService.getProcessInstanceReader().findOne(processInstanceKey);
    assertThat(processInstance).isNotEmpty();
    assertThat(processInstance.get().hasIncident()).isTrue();
    final var incident = rdbmsService.getIncidentReader().findOne(incidentKey);
    assertThat(incident).isNotEmpty();
    assertThat(incident.get().incidentKey()).isEqualTo(incidentKey);
    assertThat(incident.get().state()).isEqualTo(IncidentState.ACTIVE);

    // given
    final var incidentResolvedRecord =
        getIncidentRecord(
            IncidentIntent.RESOLVED, incidentKey, processInstanceKey, flowNodeInstanceKey);

    // when
    exporter.export(incidentResolvedRecord);

    // then
    final var flowNode2 = rdbmsService.getFlowNodeInstanceReader().findOne(flowNodeInstanceKey);
    assertThat(flowNode2).isNotEmpty();
    assertThat(flowNode2.get().incidentKey()).isNull();
    assertThat(flowNode2.get().hasIncident()).isFalse();
    final var processInstance2 =
        rdbmsService.getProcessInstanceReader().findOne(processInstanceKey);
    assertThat(processInstance2).isNotEmpty();
    assertThat(processInstance2.get().hasIncident()).isFalse();
    final var incident2 = rdbmsService.getIncidentReader().findOne(incidentKey).orElseThrow();
    assertThat(incident2.state()).isEqualTo(IncidentState.RESOLVED);
  }

  @Test
  public void shouldExportForm() {
    // given
    final var formCreatedRecord = getFormCreatedRecord(1L);

    // when
    exporter.export(formCreatedRecord);

    // then
    final var formKey = ((Form) formCreatedRecord.getValue()).getFormKey();
    final var userTask = rdbmsService.getFormReader().findOne(formKey);
    assertThat(userTask).isNotNull();
  }

  @Test
  public void shouldExportCreatedAndDeletedMapping() {
    // given
    final var mappingCreatedRecord = getMappingRecord(1L, MappingIntent.CREATED);

    // when
    exporter.export(mappingCreatedRecord);

    // then
    final var mappingId = ((MappingRecordValue) mappingCreatedRecord.getValue()).getMappingId();
    final var mapping = rdbmsService.getMappingReader().findOne(mappingId);
    assertThat(mapping).isNotNull();

    // given
    final var mappingDeletedRecord = mappingCreatedRecord.withIntent(MappingIntent.DELETED);

    // when
    exporter.export(mappingDeletedRecord);

    // then
    final var deletedMapping = rdbmsService.getMappingReader().findOne(mappingId);
    assertThat(deletedMapping).isEmpty();
  }

  @Test
  public void shouldExportAndUpdateAuthorization() {
    // given
    final var authorizationRecord =
        getAuthorizationRecord(
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
        getAuthorizationRecord(
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
        getAuthorizationRecord(
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
        getAuthorizationRecord(
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
}
