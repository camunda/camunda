/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.ByteValue;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.springframework.util.unit.DataSize;

public final class CallActivityTest {

  private static final EmbeddedBrokerRule BROKER_RULE =
      new EmbeddedBrokerRule(cfg -> cfg.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(100)));
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldCreateBpmnProcessById() {
    final String processId = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId();

    CLIENT_RULE.deployProcess(Bpmn.createExecutableProcess(processId).startEvent("v1").done());
    CLIENT_RULE.deployProcess(
        Bpmn.createExecutableProcess(processId2)
            .startEvent("v2")
            .intermediateThrowEvent()
            .zeebeOutputExpression("x", "a")
            .intermediateThrowEvent()
            .zeebeOutputExpression("x", "b")
            .intermediateThrowEvent()
            .zeebeOutputExpression("x", "c")
            .intermediateThrowEvent()
            .zeebeOutputExpression("x", "d")
            .callActivity("call-activity", c -> c.zeebeProcessId(processId))
            .endEvent("end2")
            .done());

    // when
    final ProcessInstanceEvent processInstance =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variables(Map.of("x", "x".repeat((int) ByteValue.ofKilobytes(25))))
            .send()
            .join();

    // then
    RecordingExporter.processInstanceRecords()
        .limitToProcessInstanceCompleted()
        .withElementType(BpmnElementType.PROCESS)
        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .await();
  }
}
