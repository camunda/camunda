/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.ByteValue;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

@ZeebeIntegration
final class CallActivityTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnifiedConfig(
              c -> {
                c.getCluster().getNetwork().setMaxMessageSize(DataSize.ofKilobytes(100));
                c.getProcessing().setMaxCommandsInBatch(1);
              });

  private CamundaClient client;
  private ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void createClient() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(10)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @AfterEach
  void closeClient() {
    client.close();
  }

  @Test
  void shouldRaiseIncidentWhenExceedingBatchSizeOnCallActivityActivation() {
    final String child = Strings.newRandomValidBpmnId();
    final String parent = Strings.newRandomValidBpmnId();

    resourcesHelper.deployProcess(Bpmn.createExecutableProcess(child).startEvent("v1").done());
    resourcesHelper.deployProcess(
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
        client
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
  void shouldBeAbleToResolveIncidentAfterExceedingBatchSizeOnCallActivityActivation() {
    final String child = Strings.newRandomValidBpmnId();
    final String parent = Strings.newRandomValidBpmnId();

    resourcesHelper.deployProcess(Bpmn.createExecutableProcess(child).startEvent("v1").done());
    resourcesHelper.deployProcess(
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
        client
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
    client
        .newSetVariablesCommand(processInstance.getProcessInstanceKey())
        .variables(Map.of("x", "", "a", "", "b", "", "c", "", "d", ""))
        .send()
        .join();
    client.newResolveIncidentCommand(incident.getKey()).send().join();

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
  void shouldRaiseIncidentWhenExceedingBatchSizeOnCallActivityCompletion() {
    final String child = Strings.newRandomValidBpmnId();
    final String parent = Strings.newRandomValidBpmnId();

    resourcesHelper.deployProcess(
        Bpmn.createExecutableProcess(child)
            .startEvent("child")
            .scriptTask("script1", c -> c.zeebeExpression("x").zeebeResultVariable("a"))
            .scriptTask("script2", c -> c.zeebeExpression("x").zeebeResultVariable("b"))
            .scriptTask("script3", c -> c.zeebeExpression("x").zeebeResultVariable("c"))
            .scriptTask("script4", c -> c.zeebeExpression("x").zeebeResultVariable("d"))
            .done());
    resourcesHelper.deployProcess(
        Bpmn.createExecutableProcess(parent)
            .startEvent("parent")
            .callActivity("call-activity", c -> c.zeebeProcessId(child))
            .endEvent("end2")
            .done());

    // when
    final ProcessInstanceEvent processInstance =
        client
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
