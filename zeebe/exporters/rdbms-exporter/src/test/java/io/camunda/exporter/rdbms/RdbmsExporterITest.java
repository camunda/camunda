/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.domain.VariableModel;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base Class for all tests ... Uses H2
 */
@SpringBootTest(classes = {RdbmsTestConfiguration.class})
@Testcontainers
class RdbmsExporterITest {

  private final ExporterTestController controller = new ExporterTestController();

  private final ProtocolFactory factory = new ProtocolFactory();

  @Autowired private RdbmsService rdbmsService;

  private RdbmsExporter exporter = new RdbmsExporter(rdbmsService);

  @BeforeEach
  void setUp() {
    exporter = new RdbmsExporter(rdbmsService);
    exporter.configure(new ExporterContext(null, null, 0, null, null));
    exporter.open(controller);
  }

  @Test
  public void shouldExportProcessInstance() {
    // given
    final var processInstanceRecord = getProcessInstanceStartedRecord(1L);

    // when
    exporter.export(processInstanceRecord);
    // and we do a manual flush
    rdbmsService.executionQueue().flush();

    // then
    final var key =
        ((ProcessInstanceRecordValue) processInstanceRecord.getValue()).getProcessInstanceKey();
    final var processInstance = rdbmsService.getProcessRdbmsService().findOne(key);
    assertThat(processInstance).isNotNull();
  }

  @Test
  public void shouldExportProcessInstanceAndVariables() {
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
    rdbmsService.executionQueue().flush();

    // then
    final var key =
        ((ProcessInstanceRecordValue) processInstanceRecord.getValue()).getProcessInstanceKey();
    final var processInstance = rdbmsService.getProcessRdbmsService().findOne(key);
    assertThat(processInstance).isNotNull();

    final VariableModel variable =
        rdbmsService.getVariableRdbmsService().findOne(variableCreated.getKey());
    final VariableRecordValue variableRecordValue =
        (VariableRecordValue) variableCreated.getValue();
    assertThat(variable).isNotNull();
    assertThat(variable.value()).isEqualTo(variableRecordValue.getValue());
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
}
