/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */

package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.test.util.record.RecordingExporter.signalRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.SignalRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class BroadcastSignalTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private String signalName;

  @Before
  public void init() {
    signalName = helper.getSignalName();

    final var process =
        Bpmn.createExecutableProcess().startEvent("start").signal(signalName).endEvent().done();
    CLIENT_RULE.deployProcess(process);
  }

  @Test
  public void shouldCreateProcessInstance() {
    // when
    CLIENT_RULE.getClient().newBroadcastSignalCommand().signalName(signalName).send().join();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.START_EVENT)
                .withEventType(BpmnEventType.SIGNAL)
                .limit(1)
                .getFirst()
                .getValue())
        .hasElementId("start");
  }

  @Test
  public void shouldBroadcastSignalWithoutVariables() {
    // when
    CLIENT_RULE.getClient().newBroadcastSignalCommand().signalName(signalName).send().join();

    // then
    final Record<SignalRecordValue> record =
        signalRecords(SignalIntent.BROADCASTED).withSignalName(signalName).getFirst();
    Assertions.assertThat(record.getValue()).hasSignalName(signalName);

    assertThat(record.getValue().getVariables()).isEmpty();
  }

  @Test
  public void shouldBroadcastSignalWithVariables() {
    // when
    final var variables = Map.of("x", 1, "y", 2);
    CLIENT_RULE
        .getClient()
        .newBroadcastSignalCommand()
        .signalName(signalName)
        .variables(variables)
        .send()
        .join();

    // then
    final Record<SignalRecordValue> record =
        signalRecords(SignalIntent.BROADCASTED).withSignalName(signalName).getFirst();
    Assertions.assertThat(record.getValue()).hasSignalName(signalName);

    assertThat(record.getValue().getVariables()).containsExactlyEntriesOf(variables);
  }
}
