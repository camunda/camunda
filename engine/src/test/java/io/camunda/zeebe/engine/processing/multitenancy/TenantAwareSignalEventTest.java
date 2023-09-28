/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class TenantAwareSignalEventTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

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
        .describedAs("Expect that signal was broadcasted successful")
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
  public void shouldRejectDeployProcessWithSignalForSpecificTenant() {
    // when
    final var rejection =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("signal-start")
                    .signal(signalName)
                    .endEvent()
                    .done())
            .withTenantId("custom-tenant")
            .expectRejection()
            .deploy();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to deploy new resources, but encountered the following errors:
            `process.xml`: - Process: %s
                - ERROR: Processes belonging to custom tenants are not allowed to contain elements \
            unsupported with multi-tenancy. Only the default tenant '<default>' supports these \
            elements currently: ['signal-start' of type 'SIGNAL' 'START_EVENT']. \
            See https://github.com/camunda/zeebe/issues/12653 for more details.
            """
                .formatted(processId));
  }

  @Test
  public void shouldRejectDeployProcessWithSignalCatchEventForSpecificTenant() {
    // when
    final var rejection =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .intermediateCatchEvent("signal-catch")
                    .signal(signalName)
                    .endEvent()
                    .done())
            .withTenantId("custom-tenant")
            .expectRejection()
            .deploy();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to deploy new resources, but encountered the following errors:
            `process.xml`: - Process: %s
                - ERROR: Processes belonging to custom tenants are not allowed to contain elements \
            unsupported with multi-tenancy. Only the default tenant '<default>' supports these \
            elements currently: ['signal-catch' of type 'SIGNAL' 'INTERMEDIATE_CATCH_EVENT']. \
            See https://github.com/camunda/zeebe/issues/12653 for more details.
            """
                .formatted(processId));
  }

  @Test
  public void
      shouldRejectDeployProcessWithSignalCatchEventAttachedToEventBasedGatewayForSpecificTenant() {
    // when
    final var rejection =
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
                    .timerWithDuration(Duration.ofMinutes(10))
                    .endEvent()
                    .done())
            .withTenantId("custom-tenant")
            .expectRejection()
            .deploy();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to deploy new resources, but encountered the following errors:
            `process.xml`: - Process: %s
                - ERROR: Processes belonging to custom tenants are not allowed to contain elements \
            unsupported with multi-tenancy. Only the default tenant '<default>' supports these \
            elements currently: ['signal-catch-attached' of type 'SIGNAL' 'INTERMEDIATE_CATCH_EVENT']. \
            See https://github.com/camunda/zeebe/issues/12653 for more details.
            """
                .formatted(processId));
  }

  @Test
  public void shouldRejectDeployProcessWithSignalEventSubProcessEventForSpecificTenant() {
    // when
    final var rejection =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "signal-sub",
                        sub ->
                            sub.startEvent("signal-start-event-sub").signal(signalName).endEvent())
                    .startEvent()
                    .endEvent()
                    .done())
            .withTenantId("custom-tenant")
            .expectRejection()
            .deploy();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to deploy new resources, but encountered the following errors:
            `process.xml`: - Process: %s
                - ERROR: Processes belonging to custom tenants are not allowed to contain elements \
            unsupported with multi-tenancy. Only the default tenant '<default>' supports these \
            elements currently: ['signal-start-event-sub' of type 'SIGNAL' 'START_EVENT']. \
            See https://github.com/camunda/zeebe/issues/12653 for more details.
            """
                .formatted(processId));
  }

  @Test
  public void shouldRejectDeployProcessWithSignalBoundaryEventForSpecificTenant() {
    // when
    final var rejection =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .manualTask()
                    .boundaryEvent("signal-boundary")
                    .signal(signalName)
                    .endEvent()
                    .done())
            .withTenantId("custom-tenant")
            .expectRejection()
            .deploy();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to deploy new resources, but encountered the following errors:
            `process.xml`: - Process: %s
                - ERROR: Processes belonging to custom tenants are not allowed to contain elements \
            unsupported with multi-tenancy. Only the default tenant '<default>' supports these \
            elements currently: ['signal-boundary' of type 'SIGNAL' 'BOUNDARY_EVENT']. \
            See https://github.com/camunda/zeebe/issues/12653 for more details.
            """
                .formatted(processId));
  }

  @Test
  public void shouldRejectDeployProcessWithSignalThrowEventForSpecificTenant() {
    // when
    final var rejection =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .intermediateThrowEvent("signal-throw")
                    .signal(signalName)
                    .endEvent()
                    .done())
            .withTenantId("custom-tenant")
            .expectRejection()
            .deploy();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to deploy new resources, but encountered the following errors:
            `process.xml`: - Process: %s
                - ERROR: Processes belonging to custom tenants are not allowed to contain elements \
            unsupported with multi-tenancy. Only the default tenant '<default>' supports these \
            elements currently: ['signal-throw' of type 'SIGNAL' 'INTERMEDIATE_THROW_EVENT']. \
            See https://github.com/camunda/zeebe/issues/12653 for more details.
            """
                .formatted(processId));
  }

  @Test
  public void shouldRejectDeployProcessWithSignalEndEventForSpecificTenant() {
    // when
    final var rejection =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .endEvent("signal-end")
                    .signal(signalName)
                    .done())
            .withTenantId("custom-tenant")
            .expectRejection()
            .deploy();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to deploy new resources, but encountered the following errors:
            `process.xml`: - Process: %s
                - ERROR: Processes belonging to custom tenants are not allowed to contain elements \
            unsupported with multi-tenancy. Only the default tenant '<default>' supports these \
            elements currently: ['signal-end' of type 'SIGNAL' 'END_EVENT']. \
            See https://github.com/camunda/zeebe/issues/12653 for more details.
            """
                .formatted(processId));
  }
}
