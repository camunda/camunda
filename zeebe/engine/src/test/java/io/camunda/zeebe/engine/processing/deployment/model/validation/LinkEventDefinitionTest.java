/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class LinkEventDefinitionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectDeploymentIfCatchLinkEventHaveTheSameLinkName() throws Exception {
    // given
    final Path path =
        Paths.get(
            getClass()
                .getResource("/processes/LinkEventTest.testInvalidEventLinkMultipleTarget.bpmn")
                .toURI());
    final byte[] resource = Files.readAllBytes(path);

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(resource).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains("Element: Process_05x4y1u")
        .contains(
            "ERROR: Multiple intermediate catch link event definitions with the same name 'LinkA' are not allowed.");
  }

  @Test
  public void shouldRejectDeploymentIfNoLinkName() throws Exception {
    // given
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateThrowEvent()
            .link("")
            .done();

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains("Element: linkEvent")
        .contains("ERROR: Link name must be present and not empty.");
  }

  @Test
  public void shouldRejectDeploymentIfCatchLinkEventAfterEventBasedGateway() throws Exception {
    // given
    final Path path =
        Paths.get(
            getClass()
                .getResource(
                    "/processes/LinkEventTest.testCatchLinkEventAfterEventBasedGatewayNotAllowed.bpmn")
                .toURI());
    final byte[] resource = Files.readAllBytes(path);

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(resource).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains("Element: Gateway_")
        .contains("ERROR: Event-based gateway must have at least 2 outgoing sequence flows.")
        .contains(
            "ERROR: Event-based gateway must not have an outgoing sequence flow to other elements than message/timer/signal/conditional intermediate catch events.");
  }

  @Test
  public void shouldAcceptDeploymentValidEventLink() throws Exception {
    // given
    final Path path =
        Paths.get(
            getClass().getResource("/processes/LinkEventTest.testValidEventLink.bpmn").toURI());
    final byte[] resource = Files.readAllBytes(path);

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(resource).deploy();

    // then
    assertThat(deployment.getKey())
        .describedAs("Should accept deployment valid event link process")
        .isNotNegative();
  }

  @Test
  public void shouldAcceptDeploymentValidEventLinkMultipleSources() throws Exception {
    // given
    final Path path =
        Paths.get(
            getClass()
                .getResource("/processes/LinkEventTest.testEventLinkMultipleSources.bpmn")
                .toURI());
    final byte[] resource = Files.readAllBytes(path);

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(resource).deploy();

    // then
    assertThat(deployment.getKey())
        .describedAs("Should accept deployment valid event link multiple sources process")
        .isNotNegative();
  }

  @Test
  public void shouldExecuteValidEventLink() throws Exception {
    // given
    final Path path =
        Paths.get(
            getClass().getResource("/processes/LinkEventTest.testValidEventLink.bpmn").toURI());
    final byte[] resource = Files.readAllBytes(path);

    ENGINE.deployment().withXmlResource(resource).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("Process_05x4y1u").create();

    // then
    final List<Record<ProcessInstanceRecordValue>> processInstanceEvents =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limitToProcessInstanceCompleted()
            .asList();

    assertThat(processInstanceEvents)
        .extracting(e -> e.getValue().getElementId(), Record::getIntent)
        .contains(tuple("Process_05x4y1u", ProcessInstanceIntent.COMPLETE_ELEMENT));
  }

  @Test
  public void shouldRejectDeploymentIfLinkEventNotAppearInPairs() {
    // given
    final BpmnModelInstance processDefinition = getLinkEventProcess();

    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(processDefinition).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains("Element: process")
        .contains(
            "ERROR: Can't find an catch link event for the throw link event with the name 'LinkA'.");
  }

  @Test
  public void shouldRejectDeploymentIfTheLinkCatchEventOfLinkThrowEventIsNotInTheSameScope() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess()
            .startEvent()
            .subProcess(
                "sub-1",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .intermediateThrowEvent("throw_a")
                        .link("link_a"))
            .subProcess(
                "sub-2",
                s -> {
                  s.embeddedSubProcess()
                      .startEvent()
                      .exclusiveGateway("gateway")
                      .intermediateThrowEvent("throw_b")
                      .link("link_b");
                  s.embeddedSubProcess()
                      .intermediateCatchEvent("catch_a", i -> i.link("link_a"))
                      .connectTo("gateway");
                })
            .endEvent()
            .done();

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(process).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(rejectedDeployment.getRejectionReason())
        .contains(
            "ERROR: Can't find an catch link event for the throw link event with the name 'link_a'.");
  }

  @Test
  public void shouldRejectDeploymentIfMultipleLinkCatchEventsHaveSameNameInDifferentScopes() {
    // given
    final var process =
        Bpmn.createExecutableProcess()
            .startEvent()
            .subProcess(
                "sub-1",
                s -> {
                  s.embeddedSubProcess().startEvent().endEvent();
                  s.embeddedSubProcess().intermediateCatchEvent("sub_1_catch", i -> i.link("link"));
                })
            .subProcess(
                "sub-2",
                s -> {
                  s.embeddedSubProcess().startEvent().endEvent();
                  s.embeddedSubProcess().intermediateCatchEvent("sub_2_catch", i -> i.link("link"));
                })
            .endEvent()
            .done();

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(process).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(rejectedDeployment.getRejectionReason())
        .contains(
            "ERROR: Multiple intermediate catch link event definitions with the same name 'link' are not allowed.");
  }

  @Test
  public void shouldRejectDeploymentIfThereIsNoCatchEventInTheSameScope() throws Exception {
    // given
    final Path path =
        Paths.get(getClass().getResource("/processes/invalid_link_event_subprocess.bpmn").toURI());
    final byte[] resource = Files.readAllBytes(path);

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(resource).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains("Element: process")
        .contains(
            "ERROR: Can't find an catch link event for the throw link event with the name 'foo'");
  }

  public static BpmnModelInstance getLinkEventProcess() {
    final ProcessBuilder process = Bpmn.createExecutableProcess("process");
    process.startEvent().manualTask("manualTask1").intermediateThrowEvent().link("LinkA");
    return process.linkCatchEvent().link("LinkB").manualTask("manualTask2").endEvent().done();
  }
}
