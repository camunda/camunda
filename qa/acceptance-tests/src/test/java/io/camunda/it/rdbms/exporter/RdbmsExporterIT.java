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
import static io.camunda.it.rdbms.exporter.RecordFixtures.getAuthorizationRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getDecisionDefinitionCreatedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getDecisionRequirementsCreatedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getElementActivatingRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getElementCompletedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getFormCreatedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getGroupRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getIncidentRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getMappingRuleRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getProcessDefinitionCreatedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getProcessInstanceCompletedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getProcessInstanceStartedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getRoleRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getTenantRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getUserRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getUserTaskCreatingRecord;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.exporter.rdbms.RdbmsExporterWrapper;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.filter.UserFilter.Builder;
import io.camunda.search.query.UserQuery;
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
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
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
import io.camunda.zeebe.test.util.Strings;
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
      "zeebe.broker.exporters.rdbms.args.queueSize=0",
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
            new ExporterConfiguration("foo", Map.of("queueSize", 0)),
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
  public void shouldExportRootProcessInstance() {
    // given
    final var rootProcessInstanceRecord = getProcessInstanceStartedRecord(1L, NO_PARENT_EXISTS_KEY);

    // when
    exporter.export(rootProcessInstanceRecord);

    // then
    final var key =
        ((ProcessInstanceRecordValue) rootProcessInstanceRecord.getValue()).getProcessInstanceKey();
    final var processInstance = rdbmsService.getProcessInstanceReader().findOne(key);
    assertThat(processInstance).isNotNull();

    // given
    final var rootProcessInstanceCompletedRecord =
        getProcessInstanceCompletedRecord(1L, key, NO_PARENT_EXISTS_KEY);

    // when
    exporter.export(rootProcessInstanceCompletedRecord);

    // then
    final var rootCompletedProcessInstance = rdbmsService.getProcessInstanceReader().findOne(key);
    assertThat(rootCompletedProcessInstance).isNotEmpty();
    assertThat(rootCompletedProcessInstance.get().state())
        .isEqualTo(ProcessInstanceState.COMPLETED);
    assertThat(rootCompletedProcessInstance.get().parentProcessInstanceKey()).isNull();
    assertThat(rootCompletedProcessInstance.get().parentFlowNodeInstanceKey()).isNull();
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
  public void shouldExportElement() {
    // given
    final var elementRecord = getElementActivatingRecord(1L);

    // when
    exporter.export(elementRecord);

    // then
    final var key = elementRecord.getKey();
    final var element = rdbmsService.getFlowNodeInstanceReader().findOne(key);
    assertThat(element).isNotEmpty();

    // given
    final var elementCompleteRecord = getElementCompletedRecord(1L, key);

    // when
    exporter.export(elementCompleteRecord);

    // then
    final var completedElement = rdbmsService.getFlowNodeInstanceReader().findOne(key);
    assertThat(completedElement).isNotEmpty();
    assertThat(completedElement.get().state()).isEqualTo(FlowNodeState.COMPLETED);
    // Default tree path
    assertThat(completedElement.get().treePath()).isEqualTo("1/2");
  }

  @Test
  public void shouldExportUserTask() {
    // given
    final var userTaskRecord = getUserTaskCreatingRecord(1L);

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
    final var roleId = "roleId";
    final var roleRecord = getRoleRecord(roleId, RoleIntent.CREATED);
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
    final var updateRoleRecord = getRoleRecord(roleId, RoleIntent.UPDATED);
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
    exporter.export(getRoleRecord(roleId, RoleIntent.DELETED));

    // then
    final var deletedRole = rdbmsService.getRoleReader().findOne(recordValue.getRoleId());
    assertThat(deletedRole).isEmpty();
  }

  @Test
  public void shouldExportRoleAndAddAndDeleteMember() {
    // given
    final var roleId = "roleId";
    final var roleRecord = getRoleRecord(roleId, RoleIntent.CREATED);
    final var recordValue = (RoleRecordValue) roleRecord.getValue();
    final var username = "username";
    final var userRecord = getUserRecord(1L, username, UserIntent.CREATED);
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
    exporter.export(getRoleRecord(roleId, RoleIntent.ENTITY_ADDED, username));

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
    exporter.export(getRoleRecord(roleId, RoleIntent.ENTITY_REMOVED, username));

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
    final var groupRecord = getGroupRecord(groupId, GroupIntent.CREATED);
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
    final var updateGroupRecord = getGroupRecord(groupId, GroupIntent.UPDATED);
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
    exporter.export(getGroupRecord(groupId, GroupIntent.DELETED));

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
    final var processInstanceRecord = getProcessInstanceStartedRecord(1L);
    final var processInstanceKey =
        ((ProcessInstanceRecordValue) processInstanceRecord.getValue()).getProcessInstanceKey();
    exporter.export(processInstanceRecord);
    final var elementRecord = getElementActivatingRecord(1L, processInstanceKey);
    final var elementInstanceKey = elementRecord.getKey();
    exporter.export(elementRecord);

    // when
    final var incidentKey = 42L;
    final var incidentRecord =
        getIncidentRecord(
            IncidentIntent.CREATED, incidentKey, processInstanceKey, elementInstanceKey);
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

    // given
    final var incidentResolvedRecord =
        getIncidentRecord(
            IncidentIntent.RESOLVED, incidentKey, processInstanceKey, elementInstanceKey);

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
  }

  @Test
  public void shouldExportForm() {
    // given
    final var formCreatedRecord = getFormCreatedRecord(1L);

    // when
    exporter.export(formCreatedRecord);

    // then
    final var formKey = ((Form) formCreatedRecord.getValue()).getFormKey();
    final var formEntity = rdbmsService.getFormReader().findOne(formKey);
    assertThat(formEntity).isNotNull();
  }

  @Test
  public void shouldExportCreatedAndDeletedMapping() {
    // given
    final var mappingRuleCreatedRecord = getMappingRuleRecord(1L, MappingRuleIntent.CREATED);

    // when
    exporter.export(mappingRuleCreatedRecord);

    // then
    final var mappingRuleId =
        ((MappingRuleRecordValue) mappingRuleCreatedRecord.getValue()).getMappingRuleId();
    final var mappingRule = rdbmsService.getMappingRuleReader().findOne(mappingRuleId);
    assertThat(mappingRule).isNotNull();

    // given
    final var mappingDeletedRecord = mappingRuleCreatedRecord.withIntent(MappingRuleIntent.DELETED);

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
