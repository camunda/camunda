/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.it.util.BrokerClassRuleHelper;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.Strings;
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
      new EmbeddedBrokerRule(
          cfg -> {
            cfg.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(100));
            cfg.getProcessing().setMaxCommandsInBatch(1);
          });
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldRaiseIncidentWhenExceedingBatchSizeOnCallActivityActivation() {
    final String child = Strings.newRandomValidBpmnId();
    final String parent = Strings.newRandomValidBpmnId();

    CLIENT_RULE.deployProcess(Bpmn.createExecutableProcess(child).startEvent("v1").done());
    CLIENT_RULE.deployProcess(
        Bpmn.createExecutableProcess(parent)
            .startEvent("v2")
            .scriptTask("script1", c -> c.zeebeExpression("x").zeebeResultVariable("a"))
            .scriptTask("script2", c -> c.zeebeExpression("x").zeebeResultVariable("b"))
            .scriptTask("script3", c -> c.zeebeExpression("x").zeebeResultVariable("c"))
            .scriptTask("script4", c -> c.zeebeExpression("x").zeebeResultVariable("d"))
            .callActivity("call-activity", c -> c.zeebeProcessId(child))
            .endEvent("end2")
            .done());

    // when
    final ProcessInstanceEvent processInstance =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(parent)
            .latestVersion()
            .variables(Map.of("x", "x".repeat((int) ByteValue.ofKilobytes(25))))
            .send()
            .join();

    // then
    Assertions.assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstance.getProcessInstanceKey())
                .getFirst()
                .getValue())
        .describedAs("Expected incident to be raised")
        .hasElementId("call-activity");

    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == IncidentIntent.CREATED)
                .processInstanceRecords()
                .onlyEvents()
                .withElementId("call-activity"))
        .extracting(Record::getIntent)
        .containsExactly(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .doesNotContain(
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldBeAbleToResolveIncidentAfterExceedingBatchSizeOnCallActivityActivation() {
    final String child = Strings.newRandomValidBpmnId();
    final String parent = Strings.newRandomValidBpmnId();

    CLIENT_RULE.deployProcess(Bpmn.createExecutableProcess(child).startEvent("v1").done());
    CLIENT_RULE.deployProcess(
        Bpmn.createExecutableProcess(parent)
            .startEvent("v2")
            .scriptTask("script1", c -> c.zeebeExpression("x").zeebeResultVariable("a"))
            .scriptTask("script2", c -> c.zeebeExpression("x").zeebeResultVariable("b"))
            .scriptTask("script3", c -> c.zeebeExpression("x").zeebeResultVariable("c"))
            .scriptTask("script4", c -> c.zeebeExpression("x").zeebeResultVariable("d"))
            .callActivity("call-activity", c -> c.zeebeProcessId(child))
            .endEvent("end2")
            .done());

    final ProcessInstanceEvent processInstance =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(parent)
            .latestVersion()
            .variables(Map.of("x", "x".repeat((int) ByteValue.ofKilobytes(25))))
            .send()
            .join();

    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstance.getProcessInstanceKey())
            .getFirst();

    // when
    CLIENT_RULE
        .getClient()
        .newSetVariablesCommand(processInstance.getProcessInstanceKey())
        .variables(Map.of("x", "", "a", "", "b", "", "c", "", "d", ""))
        .send()
        .join();
    CLIENT_RULE.getClient().newResolveIncidentCommand(incident.getKey()).send().join();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstance.getProcessInstanceKey())
                .limitToProcessInstanceCompleted()
                .withElementId("call-activity"))
        .extracting(Record::getIntent)
        .contains(
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldRaiseIncidentWhenExceedingBatchSizeOnCallActivityCompletion() {
    final String child = Strings.newRandomValidBpmnId();
    final String parent = Strings.newRandomValidBpmnId();

    CLIENT_RULE.deployProcess(
        Bpmn.createExecutableProcess(child)
            .startEvent("child")
            .scriptTask("script1", c -> c.zeebeExpression("x").zeebeResultVariable("a"))
            .scriptTask("script2", c -> c.zeebeExpression("x").zeebeResultVariable("b"))
            .scriptTask("script3", c -> c.zeebeExpression("x").zeebeResultVariable("c"))
            .scriptTask("script4", c -> c.zeebeExpression("x").zeebeResultVariable("d"))
            .done());
    CLIENT_RULE.deployProcess(
        Bpmn.createExecutableProcess(parent)
            .startEvent("parent")
            .callActivity("call-activity", c -> c.zeebeProcessId(child))
            .endEvent("end2")
            .done());

    // when
    final ProcessInstanceEvent processInstance =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(parent)
            .latestVersion()
            .variables(Map.of("x", "x".repeat((int) ByteValue.ofKilobytes(25))))
            .send()
            .join();

    // then
    Assertions.assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstance.getProcessInstanceKey())
                .getFirst()
                .getValue())
        .describedAs("Expected incident to be raised")
        .hasElementId("call-activity");

    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == IncidentIntent.CREATED)
                .processInstanceRecords()
                .onlyEvents()
                .withElementId("call-activity"))
        .extracting(Record::getIntent)
        .containsExactly(
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING)
        .doesNotContain(ProcessInstanceIntent.ELEMENT_COMPLETED);
  }
}
