/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.exporter;

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
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableDecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableDecisionRequirementsRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcess;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.OffsetDateTime;
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

  private final ProtocolFactory factory = new ProtocolFactory(System.nanoTime());

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
            .from(factory.generateRecord(ValueType.VARIABLE))
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
            .from(factory.generateRecord(ValueType.VARIABLE))
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

  private ImmutableRecord<RecordValue> getProcessInstanceStartedRecord(final Long position) {
    final Record<RecordValue> recordValueRecord =
        factory.generateRecord(ValueType.PROCESS_INSTANCE);
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ProcessInstanceRecordValue) recordValueRecord.getValue())
                .withBpmnElementType(BpmnElementType.PROCESS)
                .withVersion(1)
                .build())
        .build();
  }

  private ImmutableRecord<RecordValue> getProcessInstanceCompletedRecord(
      final Long position, final long processInstanceKey) {
    final Record<RecordValue> recordValueRecord =
        factory.generateRecord(ValueType.PROCESS_INSTANCE);
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withKey(processInstanceKey)
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ProcessInstanceRecordValue) recordValueRecord.getValue())
                .withProcessInstanceKey(processInstanceKey)
                .withBpmnElementType(BpmnElementType.PROCESS)
                .withVersion(1)
                .build())
        .build();
  }

  private ImmutableRecord<RecordValue> getProcessDefinitionCreatedRecord(final Long position) {
    final Record<RecordValue> recordValueRecord = factory.generateRecord(ValueType.PROCESS);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessIntent.CREATED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withPartitionId(1)
        .withValue(
            ImmutableProcess.builder()
                .from((Process) recordValueRecord.getValue())
                .withVersion(1)
                .build())
        .build();
  }

  private ImmutableRecord<RecordValue> getDecisionRequirementsCreatedRecord(final Long position) {
    final Record<RecordValue> recordValueRecord =
        factory.generateRecord(ValueType.DECISION_REQUIREMENTS);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(DecisionRequirementsIntent.CREATED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withPartitionId(1)
        .withValue(
            ImmutableDecisionRequirementsRecordValue.builder()
                .from((DecisionRequirementsRecordValue) recordValueRecord.getValue())
                .withDecisionRequirementsVersion(1)
                .build())
        .build();
  }

  private ImmutableRecord<RecordValue> getDecisionDefinitionCreatedRecord(final Long position) {
    final Record<RecordValue> recordValueRecord = factory.generateRecord(ValueType.DECISION);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(DecisionIntent.CREATED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withPartitionId(1)
        .withValue(
            ImmutableDecisionRecordValue.builder()
                .from((ImmutableDecisionRecordValue) recordValueRecord.getValue())
                .withVersion(1)
                .build())
        .build();
  }

  private ImmutableRecord<RecordValue> getFlowNodeActivatingRecord(final Long position) {
    final Record<RecordValue> recordValueRecord =
        factory.generateRecord(ValueType.PROCESS_INSTANCE);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withPartitionId(1)
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ProcessInstanceRecordValue) recordValueRecord.getValue())
                .withVersion(1)
                .build())
        .build();
  }

  private ImmutableRecord<RecordValue> getFlowNodeCompletedRecord(
      final Long position, final long elementKey) {
    final Record<RecordValue> recordValueRecord =
        factory.generateRecord(ValueType.PROCESS_INSTANCE);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withPartitionId(1)
        .withKey(elementKey)
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ProcessInstanceRecordValue) recordValueRecord.getValue())
                .withVersion(1)
                .build())
        .build();
  }

  private ImmutableRecord<RecordValue> getUserTaskCreatedRecord(final Long position) {
    final Record<RecordValue> recordValueRecord = factory.generateRecord(ValueType.USER_TASK);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(UserTaskIntent.CREATED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withPartitionId(1)
        .withValue(
            ImmutableUserTaskRecordValue.builder()
                .from((ImmutableUserTaskRecordValue) recordValueRecord.getValue())
                .withCreationTimestamp(OffsetDateTime.now().toEpochSecond())
                .withDueDate(OffsetDateTime.now().toString())
                .withFollowUpDate(OffsetDateTime.now().toString())
                .withProcessDefinitionVersion(1)
                .withCandidateUsersList(List.of("user1", "user2"))
                .withCandidateGroupsList(List.of("group1", "group2"))
                .build())
        .build();
  }
}
