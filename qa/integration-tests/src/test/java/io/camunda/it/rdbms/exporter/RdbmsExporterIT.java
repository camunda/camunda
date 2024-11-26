/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.exporter;

import static io.camunda.it.rdbms.exporter.RecordFixtures.getDecisionDefinitionCreatedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getDecisionRequirementsCreatedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getFlowNodeActivatingRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getFlowNodeCompletedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getFormCreatedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getProcessDefinitionCreatedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getProcessInstanceCompletedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getProcessInstanceStartedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getUserRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getUserTaskCreatedRecord;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.exporter.rdbms.RdbmsExporter;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@Tag("rdbms")
@SpringBootTest(classes = {RdbmsTestConfiguration.class})
@TestPropertySource(properties = {"spring.liquibase.enabled=false", "camunda.database.type=rdbms"})
class RdbmsExporterIT {

  private final ExporterTestController controller = new ExporterTestController();

  @Autowired private RdbmsService rdbmsService;

  private RdbmsExporter exporter;

  @BeforeEach
  void setUp() {
    exporter = new RdbmsExporter(rdbmsService);
    exporter.configure(
        new ExporterContext(
            null, new ExporterConfiguration("foo", Collections.emptyMap()), 1, null, null));
    exporter.open(controller);
  }

  @Test
  public void shouldExportProcessInstance() {
    // given
    final var processInstanceRecord = getProcessInstanceStartedRecord(1L);

    // when
    exporter.export(processInstanceRecord);
    // and we do a manual flush
    exporter.flushExecutionQueue();

    // then
    final var key =
        ((ProcessInstanceRecordValue) processInstanceRecord.getValue()).getProcessInstanceKey();
    final var processInstance = rdbmsService.getProcessInstanceReader().findOne(key);
    assertThat(processInstance).isNotNull();

    // given
    final var processInstanceCompletedRecord = getProcessInstanceCompletedRecord(1L, key);

    // when
    exporter.export(processInstanceCompletedRecord);
    exporter.flushExecutionQueue();

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
    // and we do a manual flush
    exporter.flushExecutionQueue();

    // then
    final var key = ((Process) processDefinitionRecord.getValue()).getProcessDefinitionKey();
    final var processDefinition = rdbmsService.getProcessDefinitionReader().findOne(key);
    assertThat(processDefinition).isNotEmpty();
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
    // and we do a manual flush
    exporter.flushExecutionQueue();

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
    // and we do a manual flush
    exporter.flushExecutionQueue();

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
    exporter.flushExecutionQueue();

    // then
    final var key = flowNodeRecord.getKey();
    final var flowNode = rdbmsService.getFlowNodeInstanceReader().findOne(key);
    assertThat(flowNode).isNotEmpty();

    // given
    final var flowNodeCompleteRecord = getFlowNodeCompletedRecord(1L, key);

    // when
    exporter.export(flowNodeCompleteRecord);
    exporter.flushExecutionQueue();

    // then
    final var completedFlowNode = rdbmsService.getFlowNodeInstanceReader().findOne(key);
    assertThat(completedFlowNode).isNotEmpty();
    assertThat(completedFlowNode.get().state()).isEqualTo(FlowNodeState.COMPLETED);
  }

  @Test
  public void shouldExportUserTask() {
    // given
    final var userTaskRecord = getUserTaskCreatedRecord(1L);

    // when
    exporter.export(userTaskRecord);
    exporter.flushExecutionQueue();

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
    // and we do a manual flush
    exporter.flushExecutionQueue();

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
    // and we do a manual flush
    exporter.flushExecutionQueue();

    // then
    final var key = ((DecisionRecordValue) decisionDefinitionRecord.getValue()).getDecisionKey();
    final var definition = rdbmsService.getDecisionDefinitionReader().findOne(key);
    assertThat(definition).isNotEmpty();
  }

  @Test
  public void shouldExportUpdateAndDeleteUser() {
    // given
    final var userRecord = getUserRecord(42L, UserIntent.CREATED);
    final var userRecordValue = ((UserRecordValue) userRecord.getValue());

    // when
    exporter.export(userRecord);
    exporter.flushExecutionQueue();

    // then
    final var user = rdbmsService.getUserReader().findOne(userRecord.getKey());
    assertThat(user).isNotEmpty();
    assertThat(user.get().userKey()).isEqualTo(userRecordValue.getUserKey());
    assertThat(user.get().username()).isEqualTo(userRecordValue.getUsername());
    assertThat(user.get().name()).isEqualTo(userRecordValue.getName());
    assertThat(user.get().email()).isEqualTo(userRecordValue.getEmail());
    assertThat(user.get().password()).isEqualTo(userRecordValue.getPassword());

    // given
    final var updateUserRecord = getUserRecord(42L, UserIntent.UPDATED);
    final var updateUserRecordValue = ((UserRecordValue) updateUserRecord.getValue());

    // when
    exporter.export(updateUserRecord);
    exporter.flushExecutionQueue();

    // then
    final var updatedUser = rdbmsService.getUserReader().findOne(userRecord.getKey());
    assertThat(updatedUser).isNotEmpty();
    assertThat(updatedUser.get().userKey()).isEqualTo(updateUserRecordValue.getUserKey());
    assertThat(updatedUser.get().username()).isEqualTo(updateUserRecordValue.getUsername());
    assertThat(updatedUser.get().name()).isEqualTo(updateUserRecordValue.getName());
    assertThat(updatedUser.get().email()).isEqualTo(updateUserRecordValue.getEmail());
    assertThat(updatedUser.get().password()).isEqualTo(updateUserRecordValue.getPassword());

    // when
    exporter.export(getUserRecord(42L, UserIntent.DELETED));
    exporter.flushExecutionQueue();

    // then
    final var deletedUser = rdbmsService.getUserReader().findOne(userRecord.getKey());
    assertThat(deletedUser).isEmpty();
  }

  @Test
  public void shouldExportForm() {
    // given
    final var formCreatedRecord = getFormCreatedRecord(1L);

    // when
    exporter.export(formCreatedRecord);
    exporter.flushExecutionQueue();

    // then
    final var formKey = ((Form) formCreatedRecord.getValue()).getFormKey();
    final var userTask = rdbmsService.getFormReader().findOne(formKey);
    assertThat(userTask).isNotNull();
  }
}
