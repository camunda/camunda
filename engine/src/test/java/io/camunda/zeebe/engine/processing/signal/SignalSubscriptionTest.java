/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.SignalSubscriptionRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public final class SignalSubscriptionTest {

  private static final String SIGNAL_NAME1 = "startSignal1";
  private static final String EVENT_ID1 = "startEventId1";
  private static final String SIGNAL_NAME2 = "startSignal2";
  private static final String EVENT_ID2 = "startEventId2";

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper brokerClassRuleHelper = new BrokerClassRuleHelper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldOpenSignalSubscriptionOnDeployment() {
    // given
    final String processId = Strings.newRandomValidBpmnId();

    // when
    engine.deployment().withXmlResource(createProcessWithOneSignalStartEvent(processId)).deploy();

    final Record<SignalSubscriptionRecordValue> subscription =
        RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
            .withBpmnProcessId(processId)
            .getFirst();

    // then
    assertThat(subscription.getValue().getCatchEventId()).isEqualTo(EVENT_ID1);
    assertThat(subscription.getValue().getSignalName()).isEqualTo(SIGNAL_NAME1);
  }

  @Test
  public void shouldOpenSubscriptionsForAllSignalStartEvents() {
    // given
    final String processId = Strings.newRandomValidBpmnId();

    // when
    engine.deployment().withXmlResource(createProcessWithTwoSignalStartEvent(processId)).deploy();

    final List<Record<SignalSubscriptionRecordValue>> subscriptions =
        RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
            .withBpmnProcessId(processId)
            .limit(2)
            .asList();

    // then
    assertThat(subscriptions.size()).isEqualTo(2);

    assertThat(subscriptions)
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(s -> tuple(s.getSignalName(), s.getCatchEventId()))
        .containsExactlyInAnyOrder(tuple(SIGNAL_NAME1, EVENT_ID1), tuple(SIGNAL_NAME2, EVENT_ID2));
  }

  @Test
  public void shouldDeleteSubscriptionForOldVersions() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    engine.deployment().withXmlResource(createProcessWithOneSignalStartEvent(processId)).deploy();

    // when
    engine.deployment().withXmlResource(createProcessWithOneSignalStartEvent(processId)).deploy();

    // then
    final List<Record<SignalSubscriptionRecordValue>> subscriptions =
        RecordingExporter.signalSubscriptionRecords()
            .withBpmnProcessId(processId)
            .limit(3)
            .asList();

    final List<Intent> intents =
        subscriptions.stream().map(Record::getIntent).collect(Collectors.toList());

    assertThat(intents)
        .containsExactly(
            SignalSubscriptionIntent.CREATED,
            SignalSubscriptionIntent.DELETED,
            SignalSubscriptionIntent.CREATED);

    final long closingProcessDefinitionKey =
        subscriptions.get(1).getValue().getProcessDefinitionKey();
    assertThat(closingProcessDefinitionKey)
        .isEqualTo(subscriptions.get(0).getValue().getProcessDefinitionKey());
  }

  @Test
  public void shouldDeleteSubscriptionsForAllSignalStartEvents() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    engine.deployment().withXmlResource(createProcessWithTwoSignalStartEvent(processId)).deploy();

    final var processDefinitionKey = RecordingExporter.processRecords().getFirst().getKey();

    // when
    engine.deployment().withXmlResource(createProcessWithTwoSignalStartEvent(processId)).deploy();

    // then
    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .withBpmnProcessId(processId)
                .limit(2))
        .extracting(r -> r.getValue().getProcessDefinitionKey(), r -> r.getValue().getSignalName())
        .contains(
            tuple(processDefinitionKey, SIGNAL_NAME1), tuple(processDefinitionKey, SIGNAL_NAME2));
  }

  @Test
  public void shouldOpenSingleSignalSubscriptionOnMultipleDeployments() {
    // give
    final String processId = "signalProcess";
    final var process = createProcessWithOneSignalStartEvent(processId);

    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.deployment().withXmlResource(process).deploy();

    // then
    // deploy an empty deployment, so we can use the position to limit the recorded stream
    final var position = engine.deployment().expectRejection().deploy().getPosition();
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= position)
                .filter(r -> r.getValueType() == ValueType.SIGNAL_SUBSCRIPTION))
        .describedAs("Expect only one signal start event subscription for duplicate deployments")
        .hasSize(1);
  }

  private static BpmnModelInstance createProcessWithOneSignalStartEvent(final String processId) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent(EVENT_ID1)
        .signal(s -> s.name(SIGNAL_NAME1).id("startSignalId"))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance createProcessWithTwoSignalStartEvent(final String processId) {
    final ProcessBuilder process = Bpmn.createExecutableProcess(processId);
    process.startEvent(EVENT_ID1).signal(s -> s.name(SIGNAL_NAME1).id("startSignalId1")).endEvent();
    process.startEvent(EVENT_ID2).signal(s -> s.name(SIGNAL_NAME2).id("startSignalId2")).endEvent();
    return process.done();
  }
}
