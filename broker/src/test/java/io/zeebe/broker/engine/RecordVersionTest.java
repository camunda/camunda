/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.test.broker.protocol.commandapi.CommandApiRule;
import io.zeebe.test.util.BrokerClassRuleHelper;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.util.VersionUtil;
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
  private static final CommandApiRule API_RULE = new CommandApiRule(BROKER_RULE::getAtomix);
  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(API_RULE);
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void deploymentRecordsShouldHaveBrokerVersion() {
    // given
    final var workflow = Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();

    deployWorkflow(workflow);

    // then
    assertThat(RecordingExporter.workflowRecords().limit(1))
        .hasSize(1)
        .extracting(Record::getBrokerVersion)
        .containsOnly(EXPECTED_VERSION);

    assertThat(RecordingExporter.deploymentRecords().limit(3))
        .hasSize(3)
        .extracting(Record::getBrokerVersion)
        .containsOnly(EXPECTED_VERSION);
  }

  @Test
  public void workflowInstanceRecordsShouldHaveBrokerVersion() {
    // given
    final var workflow = Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();

    deployWorkflow(workflow);
    final var workflowInstanceKey = createWorkflowInstance(PROCESS_ID);

    // then
    assertThat(RecordingExporter.records().limitToWorkflowInstance(workflowInstanceKey))
        .extracting(Record::getBrokerVersion)
        .containsOnly(EXPECTED_VERSION);
  }

  @Test
  public void messageSubscriptionRecordsShouldHaveBrokerVersion() {
    // given
    final var workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateCatchEvent(
                "catch", e -> e.message(m -> m.name("test").zeebeCorrelationKeyExpression("123")))
            .endEvent()
            .done();

    deployWorkflow(workflow);
    final var workflowInstanceKey = createWorkflowInstance(PROCESS_ID);

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2))
        .hasSize(2)
        .extracting(Record::getBrokerVersion)
        .containsOnly(EXPECTED_VERSION);

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2))
        .hasSize(2)
        .extracting(Record::getBrokerVersion)
        .containsOnly(EXPECTED_VERSION);
  }

  private void deployWorkflow(final BpmnModelInstance workflow) {
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, workflow);
    final byte[] resource = outStream.toByteArray();

    API_RULE
        .createCmdRequest()
        .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
        .command()
        .put("resources", List.of(Map.of("resourceName", "process.bpmn", "resource", resource)))
        .done()
        .send();
  }

  private long createWorkflowInstance(final String processId) {
    final var response =
        API_RULE
            .createCmdRequest()
            .type(ValueType.WORKFLOW_INSTANCE_CREATION, WorkflowInstanceCreationIntent.CREATE)
            .command()
            .put("bpmnProcessId", processId)
            .done()
            .sendAndAwait();

    return (Long) response.getValue().get("workflowInstanceKey");
  }
}
