/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.inmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

public class InMemoryEngineTest {

  @Test
  void shouldStartAndStopEngine() {
    // given
    final InMemoryEngine engine = InMemoryEngineFactory.create();
    assertThat(engine).isNotNull();

    // when/then
    engine.start();
    engine.stop();

    assertThatNoException();
  }

  @Test
  void shouldWriteAndReadCommand() {
    // given
    final InMemoryEngine engine = InMemoryEngineFactory.create();
    engine.start();

    // when
    final MessageRecord command =
        new MessageRecord().setName("A").setCorrelationKey("key-1").setTimeToLive(0);
    final EventPosition eventPosition =
        engine.writeCommand(ValueType.MESSAGE, MessageIntent.PUBLISH, command);

    // then
    engine.waitForIdleState(Duration.ofSeconds(10));

    assertThat(engine.getRecordStreamView().getRecords())
        .extracting(Record::getPosition)
        .contains(eventPosition.position());
  }

  @Test
  void shouldCreateProcessInstance() {
    // given
    final InMemoryEngine engine = InMemoryEngineFactory.create();
    engine.start();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
    final String bpmnXml = Bpmn.convertToString(process);

    // when
    final EventPosition deploymentCommandPosition =
        engine.writeCommand(ProcessTestCommands.deployResources("process.bpmn", bpmnXml));
    awaitCommandProcess(
        engine, deploymentCommandPosition, ValueType.DEPLOYMENT, DeploymentIntent.CREATED);

    final EventPosition creationCommandPosition =
        engine.writeCommand(ProcessTestCommands.createProcessInstance("process", "{}"));
    awaitCommandProcess(
        engine,
        creationCommandPosition,
        ValueType.PROCESS_INSTANCE_CREATION,
        ProcessInstanceCreationIntent.CREATED);

    // then
    engine.waitForIdleState(Duration.ofSeconds(10));

    assertThat(engine.getRecordStreamView().getRecords())
        .filteredOn(record -> record.getValueType() == ValueType.PROCESS_INSTANCE)
        .extracting(
            Record::getIntent,
            record -> ((ProcessInstanceRecordValue) record.getValue()).getBpmnElementType())
        .containsSubsequence(
            tuple(ProcessInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.PROCESS),
            tuple(ProcessInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.START_EVENT),
            tuple(ProcessInstanceIntent.ELEMENT_COMPLETED, BpmnElementType.START_EVENT),
            tuple(ProcessInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.END_EVENT),
            tuple(ProcessInstanceIntent.ELEMENT_COMPLETED, BpmnElementType.END_EVENT),
            tuple(ProcessInstanceIntent.ELEMENT_COMPLETED, BpmnElementType.PROCESS));
  }

  private void awaitCommandProcess(
      final InMemoryEngine engine,
      final EventPosition eventPosition,
      final ValueType expectedValueType,
      final Intent expectedIntent) {

    engine.waitForIdleState(Duration.ofSeconds(10));

    final List<Record<?>> commandResults =
        engine.getRecordStreamView().getRecords().stream()
            .filter(record -> record.getSourceRecordPosition() == eventPosition.position())
            .toList();

    assertThat(commandResults)
        .extracting(Record::getRecordType, Record::getValueType, Record::getIntent)
        .contains(tuple(RecordType.EVENT, expectedValueType, expectedIntent));
  }
}
