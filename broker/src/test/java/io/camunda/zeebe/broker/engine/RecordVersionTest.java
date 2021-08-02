/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.test.broker.protocol.commandapi.CommandApiRule;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.VersionUtil;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class RecordVersionTest {

  private static final String PROCESS_ID = "process";

  private static final String EXPECTED_VERSION =
      VersionUtil.getVersion().replaceAll("-SNAPSHOT", "");

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final CommandApiRule API_RULE = new CommandApiRule(BROKER_RULE::getAtomixCluster);
  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(API_RULE);
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void deploymentRecordsShouldHaveBrokerVersion() {
    // given
    final var process = Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();

    deployProcess(process);

    // then
    assertThat(RecordingExporter.processRecords().limit(1))
        .hasSize(1)
        .extracting(Record::getBrokerVersion)
        .containsOnly(EXPECTED_VERSION);

    assertThat(RecordingExporter.deploymentRecords().limit(3))
        .hasSize(3)
        .extracting(Record::getBrokerVersion)
        .containsOnly(EXPECTED_VERSION);
  }

  @Test
  public void processInstanceRecordsShouldHaveBrokerVersion() {
    // given
    final var process = Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();

    deployProcess(process);
    final var processInstanceKey = createProcessInstance(PROCESS_ID);

    // then
    assertThat(RecordingExporter.records().limitToProcessInstance(processInstanceKey))
        .extracting(Record::getBrokerVersion)
        .containsOnly(EXPECTED_VERSION);
  }

  @Test
  public void messageSubscriptionRecordsShouldHaveBrokerVersion() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateCatchEvent(
                "catch", e -> e.message(m -> m.name("test").zeebeCorrelationKeyExpression("123")))
            .endEvent()
            .done();

    deployProcess(process);
    final var processInstanceKey = createProcessInstance(PROCESS_ID);

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .hasSize(2)
        .extracting(Record::getBrokerVersion)
        .containsOnly(EXPECTED_VERSION);

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .hasSize(2)
        .extracting(Record::getBrokerVersion)
        .containsOnly(EXPECTED_VERSION);
  }

  private void deployProcess(final BpmnModelInstance process) {
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, process);
    final byte[] resource = outStream.toByteArray();

    API_RULE
        .createCmdRequest()
        .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
        .command()
        .put("resources", List.of(Map.of("resourceName", "process.bpmn", "resource", resource)))
        .done()
        .send();
  }

  private long createProcessInstance(final String processId) {
    final var response =
        API_RULE
            .createCmdRequest()
            .type(ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationIntent.CREATE)
            .command()
            .put("bpmnProcessId", processId)
            .done()
            .sendAndAwait();

    return (Long) response.getValue().get("processInstanceKey");
  }
}
