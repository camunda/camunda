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
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcess;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
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
}
