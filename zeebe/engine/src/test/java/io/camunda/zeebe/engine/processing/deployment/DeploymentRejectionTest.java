/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class DeploymentRejectionTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectDeploymentIfUsedInvalidMessage() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess().startEvent().intermediateCatchEvent("invalidMessage").done();

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(process).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment.getRecordType())
        .isEqualTo(RecordType.COMMAND_REJECTION);
  }

  @Test
  public void shouldRejectDeploymentIfNotValidDesignTimeAspect() {
    // given
    final String resource = "/processes/invalid_process.bpmn";

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlClasspathResource(resource).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains("ERROR: Must have at least one start event");
  }

  @Test
  public void shouldRejectDeploymentIfNotValidRuntimeAspect() {
    // given
    final String resource = "/processes/invalid_process_condition.bpmn";

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlClasspathResource(resource).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains("Element: flow2 > conditionExpression")
        .contains("ERROR: failed to parse expression");
  }

  @Test
  public void shouldRejectDeploymentIfOneResourceIsNotValid() {
    // given
    final String resource1 = "/processes/invalid_process.bpmn";
    final String resource2 = "/processes/collaboration.bpmn";

    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE
            .deployment()
            .withXmlClasspathResource(resource1)
            .withXmlClasspathResource(resource2)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectDeploymentIfNoResources() {
    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason("Expected to deploy at least one resource, but none given");
  }

  @Test
  public void shouldRejectDeploymentIfNotParsable() {
    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE
            .deployment()
            .withXmlResource("not a process".getBytes(UTF_8))
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectDeploymentWithDuplicateResources() {
    // given
    final BpmnModelInstance definition1 =
        Bpmn.createExecutableProcess("process1").startEvent().done();
    final BpmnModelInstance definition2 =
        Bpmn.createExecutableProcess("process2").startEvent().done();
    final BpmnModelInstance definition3 =
        Bpmn.createExecutableProcess("process2")
            .startEvent()
            .serviceTask("task", (t) -> t.zeebeJobType("j").zeebeTaskHeader("k", "v"))
            .done();

    // when
    final Record<DeploymentRecordValue> deploymentRejection =
        ENGINE
            .deployment()
            .withXmlResource("p1.bpmn", definition1)
            .withXmlResource("p2.bpmn", definition2)
            .withXmlResource("p3.bpmn", definition3)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentRejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to deploy new resources, but encountered the following errors:\n"
                + "Duplicated process id in resources 'p2.bpmn' and 'p3.bpmn'");
  }

  @Test
  public void shouldRejectDeploymentWithInvalidTimerStartEventExpression() {
    // given
    final BpmnModelInstance definition =
        Bpmn.createExecutableProcess("process1")
            .startEvent("start-event-1")
            .timerWithCycleExpression("INVALID_CYCLE_EXPRESSION")
            .done();

    // when
    final Record<DeploymentRecordValue> deploymentRejection =
        ENGINE.deployment().withXmlResource("p1.bpmn", definition).expectRejection().deploy();

    // then
    Assertions.assertThat(deploymentRejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to deploy new resources, but encountered the following errors:
            'p1.bpmn': - Element: start-event-1
                - ERROR: Invalid timer cycle expression (Expected result of the expression 'INVALID_CYCLE_EXPRESSION' to be 'STRING', but was 'NULL'. \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'INVALID_CYCLE_EXPRESSION')
            """);
  }

  // https://github.com/camunda/camunda/issues/8026
  @Test
  public void shouldRejectDeploymentOfSAXException() {
    // given
    final String resource = "/processes/saxexception-error-8026.bpmn";

    // when
    final Record<DeploymentRecordValue> deploymentRejection =
        ENGINE.deployment().withXmlClasspathResource(resource).expectRejection().deploy();

    // then
    Assertions.assertThat(deploymentRejection)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(deploymentRejection.getRejectionReason())
        .describedAs("rejection should contain enough detail rejection reason")
        .contains("saxexception-error-8026.bpmn")
        .contains("cvc-complex-type.3.2.2")
        .contains("stroke")
        .contains("bpmndi:BPMNPlane");
  }

  // https://github.com/camunda/camunda/issues/9542
  @Test
  public void shouldRejectDeploymentIfNoExecutableProcess() {
    // given
    final String resource = "/processes/non-executable-process-single.bpmn";

    // when
    final Record<DeploymentRecordValue> deploymentRejection =
        ENGINE.deployment().withXmlClasspathResource(resource).expectRejection().deploy();

    // then
    Assertions.assertThat(deploymentRejection)
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    assertThat(deploymentRejection.getRejectionReason())
        .contains("Must contain at least one executable process");
  }

  @Test
  public void shouldDoAtomicDeployments() {
    // given
    final BpmnModelInstance invalidProcess =
        Bpmn.createExecutableProcess("invalid_process_without_start_event").done();
    final BpmnModelInstance validProcess =
        Bpmn.createExecutableProcess("valid_process").startEvent().task().endEvent().done();

    // when
    ENGINE
        .deployment()
        .withXmlResource(invalidProcess)
        .withXmlResource(validProcess)
        .expectRejection()
        .deploy();

    // then
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getRecordType() == RecordType.COMMAND_REJECTION)
                .collect(Collectors.toList()))
        .extracting(Record::getIntent, Record::getRecordType)
        .doesNotContain(
            tuple(ProcessIntent.CREATED, RecordType.EVENT),
            tuple(DeploymentIntent.CREATED, RecordType.EVENT),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT));
  }

  @Test
  public void
      shouldRejectDeploymentIfCalledProcessNotIncludedForCallActivityWithBindingTypeDeployment() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity(
                "callActivity",
                builder ->
                    builder
                        .zeebeBindingType(ZeebeBindingType.deployment)
                        .zeebeProcessId("test-process"))
            .endEvent()
            .done();

    // when
    final var rejectedDeployment =
        ENGINE.deployment().withXmlResource("process.bpmn", process).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .isEqualTo(
            """
            Expected to deploy new resources, but encountered the following errors:
            'process.bpmn':
            - Element: callActivity > extensionElements > calledElement
                - ERROR: Expected to find process with id 'test-process' in current deployment, but not found.
            """);
  }

  @Test
  public void
      shouldRejectDeploymentIfCalledDecisionNotIncludedForBusinessRuleTaskWithBindingTypeDeployment() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .businessRuleTask(
                "businessRuleTask",
                builder ->
                    builder
                        .zeebeBindingType(ZeebeBindingType.deployment)
                        .zeebeCalledDecisionId("test-decision")
                        .zeebeResultVariable("foo"))
            .endEvent()
            .done();

    // when
    final var rejectedDeployment =
        ENGINE.deployment().withXmlResource("process.bpmn", process).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .isEqualTo(
            """
            Expected to deploy new resources, but encountered the following errors:
            'process.bpmn':
            - Element: businessRuleTask > extensionElements > calledDecision
                - ERROR: Expected to find decision with id 'test-decision' in current deployment, but not found.
            """);
  }

  @Test
  public void shouldRejectDeploymentIfLinkedFormNotIncludedForUserTaskWithBindingTypeDeployment() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "userTask",
                builder ->
                    builder
                        .zeebeFormBindingType(ZeebeBindingType.deployment)
                        .zeebeFormId("test-form"))
            .endEvent()
            .done();

    // when
    final var rejectedDeployment =
        ENGINE.deployment().withXmlResource("process.bpmn", process).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .isEqualTo(
            """
            Expected to deploy new resources, but encountered the following errors:
            'process.bpmn':
            - Element: userTask > extensionElements > formDefinition
                - ERROR: Expected to find form with id 'test-form' in current deployment, but not found.
            """);
  }

  @Test
  public void
      shouldRejectDeploymentIfTargetResourceNotIncludedForBindingTypeDeploymentInAnyProcess() {
    // given
    final var process1 =
        Bpmn.createExecutableProcess("process-1")
            .startEvent()
            .callActivity(
                "callActivity1",
                builder ->
                    builder
                        .zeebeBindingType(ZeebeBindingType.deployment)
                        .zeebeProcessId("test-process-1"))
            .endEvent()
            .done();
    final var process2 =
        Bpmn.createExecutableProcess("process-2")
            .startEvent()
            .callActivity(
                "callActivity2",
                builder ->
                    builder
                        .zeebeBindingType(ZeebeBindingType.deployment)
                        .zeebeProcessId("test-process-2"))
            .endEvent()
            .done();

    // when
    final var rejectedDeployment =
        ENGINE
            .deployment()
            .withXmlResource("process 1.bpmn", process1)
            .withXmlResource("process 2.bpmn", process2)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .isEqualTo(
            """
            Expected to deploy new resources, but encountered the following errors:
            'process 1.bpmn':
            - Element: callActivity1 > extensionElements > calledElement
                - ERROR: Expected to find process with id 'test-process-1' in current deployment, but not found.

            'process 2.bpmn':
            - Element: callActivity2 > extensionElements > calledElement
                - ERROR: Expected to find process with id 'test-process-2' in current deployment, but not found.
            """);
  }

  @Test
  public void
      shouldRejectDeploymentIfTargetResourceNotIncludedForBindingTypeDeploymentInMultipleElements() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity(
                "callActivity",
                builder ->
                    builder
                        .zeebeBindingType(ZeebeBindingType.deployment)
                        .zeebeProcessId("test-process"))
            .businessRuleTask(
                "businessRuleTask",
                builder ->
                    builder
                        .zeebeBindingType(ZeebeBindingType.deployment)
                        .zeebeCalledDecisionId("test-decision")
                        .zeebeResultVariable("foo"))
            .endEvent()
            .done();

    // when
    final var rejectedDeployment =
        ENGINE.deployment().withXmlResource("process.bpmn", process).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .startsWith("Expected to deploy new resources, but encountered the following errors:")
        // the order of the element errors for a particular resource is not deterministic, so the
        // assertion checks that one of the possible variants is included
        .containsAnyOf(
            """
            'process.bpmn':
            - Element: businessRuleTask > extensionElements > calledDecision
                - ERROR: Expected to find decision with id 'test-decision' in current deployment, but not found.
            - Element: callActivity > extensionElements > calledElement
                - ERROR: Expected to find process with id 'test-process' in current deployment, but not found.
            """,
            """
            'process.bpmn':
            - Element: callActivity > extensionElements > calledElement
                - ERROR: Expected to find process with id 'test-process' in current deployment, but not found.
            - Element: businessRuleTask > extensionElements > calledDecision
                - ERROR: Expected to find decision with id 'test-decision' in current deployment, but not found.
            """);
  }

  @Test
  public void shouldNotRejectDeploymentForBindingTypeDeploymentIfTargetIdIsExpression() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity(
                "activity",
                builder ->
                    builder
                        .zeebeBindingType(ZeebeBindingType.deployment)
                        .zeebeProcessIdExpression("processIdVar"))
            .businessRuleTask(
                "businessRuleTask",
                builder ->
                    builder
                        .zeebeBindingType(ZeebeBindingType.deployment)
                        .zeebeCalledDecisionIdExpression("decisionIdVar")
                        .zeebeResultVariable("foo"))
            .userTask(
                "userTask",
                builder ->
                    builder
                        .zeebeFormBindingType(ZeebeBindingType.deployment)
                        .zeebeFormId("=formIdVar"))
            .endEvent()
            .done();

    // when
    final var deployment = ENGINE.deployment().withXmlResource("process.bpmn", process).deploy();

    // then
    Assertions.assertThat(deployment)
        .hasRecordType(RecordType.EVENT)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasIntent(DeploymentIntent.CREATED);
  }

  @Test
  public void
      shouldNotRejectDeploymentIfTargetResourceMissingForBindingTypeDeploymentInNonExecutableProcess() {
    // given
    final String resource = "/processes/non-executable-process-deployment-binding.bpmn";

    // when
    final var deployment = ENGINE.deployment().withXmlClasspathResource(resource).deploy();

    // then
    Assertions.assertThat(deployment)
        .hasRecordType(RecordType.EVENT)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasIntent(DeploymentIntent.CREATED);
  }
}
