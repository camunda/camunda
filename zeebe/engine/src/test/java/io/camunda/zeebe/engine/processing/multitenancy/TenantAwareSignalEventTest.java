/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class TenantAwareSignalEventTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(config -> config.getMultiTenancy().setEnabled(true));

  @Rule public final TestWatcher testWatcher = new RecordingExporterTestWatcher();
  private String processId;
  private String signalName;

  @Before
  public void setup() {
    processId = Strings.newRandomValidBpmnId();
    signalName = "signal-%s".formatted(processId);
  }

  @Test
  public void shouldBroadcastSignalForDefaultTenant() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent("signal-start")
                .signal(signalName)
                .endEvent()
                .done())
        .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .deploy();

    // when
    final var broadcasted = ENGINE.signal().withSignalName(signalName).broadcast();

    // then
    assertThat(broadcasted)
        .describedAs("Expect that signal was broadcast successfully")
        .hasIntent(SignalIntent.BROADCASTED);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementId("signal-start")
                .getFirst())
        .describedAs("Expect that process instance was created")
        .isNotNull();
  }

  @Test
  public void shouldBroadcastSignalForSpecificTenant() {
    // given
    final String tenantId = "custom-tenant";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent("signal-start")
                .signal(signalName)
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();

    // when
    final var broadcasted =
        ENGINE.signal().withSignalName(signalName).withTenantId(tenantId).broadcast();

    // then
    assertThat(broadcasted)
        .describedAs("Expect that signal was broadcast successfully")
        .hasIntent(SignalIntent.BROADCASTED);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementId("signal-start")
                .withTenantId(tenantId)
                .getFirst())
        .describedAs("Expect that process instance was created")
        .isNotNull();
  }

  @Test
  public void shouldBroadcastToSignalCatchEventForSpecificTenant() {
    // given
    final String tenantId = "custom-tenant";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("signal-catch")
                .signal(signalName)
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(tenantId).create();

    // when
    final var broadcasted =
        ENGINE.signal().withSignalName(signalName).withTenantId(tenantId).broadcast();

    // then
    assertThat(broadcasted)
        .describedAs("Expect that signal was broadcast successfully")
        .hasIntent(SignalIntent.BROADCASTED);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementId("signal-catch")
                .withTenantId(tenantId)
                .getFirst())
        .describedAs("Expect that a signal catch event was activated")
        .isNotNull();
  }

  @Test
  public void shouldBroadcastToSignalCatchEventAttachedToEventBasedGatewayForSpecificTenant() {
    // given
    final String tenantId = "custom-tenant";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .eventBasedGateway()
                .intermediateCatchEvent("signal-catch-attached")
                .signal(signalName)
                .endEvent()
                .moveToLastGateway()
                .intermediateCatchEvent()
                .message(m -> m.name("message").zeebeCorrelationKeyExpression("123"))
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(tenantId).create();

    // when
    final var broadcasted =
        ENGINE.signal().withSignalName(signalName).withTenantId(tenantId).broadcast();

    // then
    assertThat(broadcasted)
        .describedAs("Expect that signal was broadcast successfully")
        .hasIntent(SignalIntent.BROADCASTED);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementId("signal-catch-attached")
                .withTenantId(tenantId)
                .getFirst())
        .describedAs("Expect that a signal catch event was activated")
        .isNotNull();
  }

  @Test
  public void shouldBroadcastToSignalEventSubProcessEventForSpecificTenant() {
    // given
    final String tenantId = "custom-tenant";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .eventSubProcess(
                    "signal-sub",
                    sub -> sub.startEvent("signal-start-event-sub").signal(signalName).endEvent())
                .startEvent()
                .userTask()
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(tenantId).create();

    // when
    final var broadcasted =
        ENGINE.signal().withSignalName(signalName).withTenantId(tenantId).broadcast();

    // then
    assertThat(broadcasted)
        .describedAs("Expect that signal was broadcast successfully")
        .hasIntent(SignalIntent.BROADCASTED);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementId("signal-start-event-sub")
                .withTenantId(tenantId)
                .getFirst())
        .describedAs("Expect that an event sub-process was activated")
        .isNotNull();
  }

  @Test
  public void shouldBroadcastToSignalBoundaryEventForSpecificTenant() {
    // given
    final String tenantId = "custom-tenant";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask()
                .boundaryEvent("signal-boundary")
                .signal(signalName)
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(tenantId).create();

    // when
    final var broadcasted =
        ENGINE.signal().withSignalName(signalName).withTenantId(tenantId).broadcast();

    // then
    assertThat(broadcasted)
        .describedAs("Expect that signal was broadcast successfully")
        .hasIntent(SignalIntent.BROADCASTED);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementId("signal-boundary")
                .withTenantId(tenantId)
                .getFirst())
        .describedAs("Expect that a signal boundary event was activated")
        .isNotNull();
  }

  @Test
  public void shouldThrowIntermediateSignalEventForSpecificTenant() {
    // given
    final String tenantId = "custom-tenant";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateThrowEvent("signal-throw")
                .signal(signalName)
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(tenantId).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementId("signal-throw")
                .withTenantId(tenantId)
                .getFirst())
        .describedAs("Expect that a signal throw event was activated")
        .isNotNull();
    assertThat(
            RecordingExporter.signalRecords()
                .withSignalName(signalName)
                .withIntent(SignalIntent.BROADCASTED)
                .withTenantId(tenantId)
                .getFirst())
        .describedAs("Expect that signal is thrown")
        .isNotNull();
  }

  @Test
  public void shouldThrowSignalEndEventForSpecificTenant() {
    // given
    final String tenantId = "custom-tenant";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .endEvent("signal-throw-end")
                .signal(signalName)
                .done())
        .withTenantId(tenantId)
        .deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(tenantId).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementId("signal-throw-end")
                .withTenantId(tenantId)
                .getFirst())
        .describedAs("Expect that a signal end event was activated")
        .isNotNull();
    assertThat(
            RecordingExporter.signalRecords()
                .withSignalName(signalName)
                .withIntent(SignalIntent.BROADCASTED)
                .withTenantId(tenantId)
                .getFirst())
        .describedAs("Expect that signal is thrown")
        .isNotNull();
  }

  @Test
  public void shouldCatchThrownSignalEndEventForSpecificTenant() {
    // given
    final String tenantId = "custom-tenant";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .endEvent("signal-throw-end")
                .signal(signalName)
                .done())
        .withTenantId(tenantId)
        .deploy();
    final String signalCatchingProcess = processId + "catch";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(signalCatchingProcess)
                .startEvent("signal-start")
                .signal(signalName)
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(tenantId).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementId("signal-throw-end")
                .withTenantId(tenantId)
                .getFirst())
        .describedAs("Expect that a signal throw event was created")
        .isNotNull();
    assertThat(
            RecordingExporter.signalRecords()
                .withSignalName(signalName)
                .withIntent(SignalIntent.BROADCASTED)
                .withTenantId(tenantId)
                .getFirst())
        .describedAs("Expect that signal is thrown")
        .isNotNull();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(signalCatchingProcess)
                .withElementId("signal-start")
                .withTenantId(tenantId)
                .getFirst())
        .describedAs("Expect that process instance was created")
        .isNotNull();
  }

  @Test
  public void shouldNotCatchThrownSignalEndEventForDifferentTenant() {
    // given
    final String tenantIdA = "custom-tenant-a";
    final String tenantIdB = "custom-tenant-b";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .endEvent("signal-throw-end")
                .signal(signalName)
                .done())
        .withTenantId(tenantIdA)
        .deploy();
    final String signalCatchingProcess = processId + "catch";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(signalCatchingProcess)
                .startEvent("signal-start")
                .signal(signalName)
                .endEvent()
                .done())
        .withTenantId(tenantIdB)
        .deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(tenantIdA).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementId("signal-throw-end")
                .withTenantId(tenantIdA)
                .getFirst())
        .describedAs("Expect that a signal throw event was created")
        .isNotNull();
    assertThat(
            RecordingExporter.signalRecords()
                .withSignalName(signalName)
                .withIntent(SignalIntent.BROADCASTED)
                .withTenantId(tenantIdA)
                .getFirst())
        .describedAs("Expect that signal is thrown")
        .isNotNull();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(signalCatchingProcess)
                .withElementId("signal-start")
                .withTenantId(tenantIdB)
                .exists())
        .describedAs("Expect that process instance was not created")
        .isFalse();
  }
}
