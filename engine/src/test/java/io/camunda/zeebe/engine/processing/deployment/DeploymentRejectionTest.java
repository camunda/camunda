/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class DeploymentRejectionTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String TOO_LARGE_DEPLOYMENT_RESOURCE = "/processes/too_large_process.bpmn";

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
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
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
                    - ERROR: Invalid timer cycle expression (failed to evaluate expression 'INVALID_CYCLE_EXPRESSION': no variable found for name 'INVALID_CYCLE_EXPRESSION')
                """);
  }

  @Test
  public void shouldRejectDeploymentIfResourceIsTooLarge() {
    // when
    final Record<DeploymentRecordValue> deploymentRejection =
        ENGINE
            .deployment()
            .withXmlClasspathResource(TOO_LARGE_DEPLOYMENT_RESOURCE)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(deploymentRejection)
        .hasRejectionType(RejectionType.EXCEEDED_BATCH_RECORD_SIZE)
        .hasRejectionReason("");
  }

  // https://github.com/camunda/zeebe/issues/8026
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

    /*
     The detail rejection reason is an i18n SAXException, so here use the matches instead.

     In Chinese
     "Expected to deploy new resources, but encountered the following errors:
     '/processes/saxexception-error-8026.bpmn': SAXException Error: URI=null Line=33:
     cvc-complex-type.3.2.2: 元素 'bpmndi:BPMNPlane' 中不允许出现属性 'stroke'。"

     In English
     "Expected to deploy new resources, but encountered the following errors:
     '/processes/saxexception-error-8026.bpmn': SAXException Error: URI=null Line=33:
     cvc-complex-type.3.2.2: Attribute 'stroke' is not allowed to appear in element
     'bpmndi:BPMNPlane'.
    */

    assertThat(deploymentRejection.getRejectionReason())
        .describedAs("rejection should contain enough detail rejection reason")
        .contains("SAXException Error")
        .contains("cvc-complex-type.3.2.2")
        .contains("stroke")
        .contains("bpmndi:BPMNPlane");
  }
}
