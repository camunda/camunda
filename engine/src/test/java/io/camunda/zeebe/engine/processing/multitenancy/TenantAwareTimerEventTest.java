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
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
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

public class TenantAwareTimerEventTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher testWatcher = new RecordingExporterTestWatcher();
  private String processId;

  @Before
  public void setup() {
    processId = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldCreateTimerForDefaultTenant() {
    // when
    final var deployed =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("timer-start")
                    .timerWithDuration(Duration.ofMinutes(10))
                    .endEvent()
                    .done())
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .deploy();

    // then
    assertThat(deployed)
        .describedAs("Expect that process with timer was deployed successful")
        .hasIntent(DeploymentIntent.CREATED);

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(
                    deployed.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey())
                .getFirst())
        .describedAs("Expect that timer was created")
        .isNotNull();
  }

  @Test
  public void shouldRejectDeployProcessWithTimerForSpecificTenant() {
    // when
    final var rejection =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("timer-start")
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
            elements currently: ['timer-start' of type 'TIMER' 'START_EVENT']. \
            See https://github.com/camunda/zeebe/issues/12653 for more details.
            """
                .formatted(processId));
  }

  @Test
  public void shouldRejectDeployProcessWithTimerCatchEventForSpecificTenant() {
    // when
    final var rejection =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .intermediateCatchEvent("timer-catch")
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
            elements currently: ['timer-catch' of type 'TIMER' 'INTERMEDIATE_CATCH_EVENT']. \
            See https://github.com/camunda/zeebe/issues/12653 for more details.
            """
                .formatted(processId));
  }

  @Test
  public void
      shouldRejectDeployProcessWithTimerCatchEventAttachedToEventBasedGatewayForSpecificTenant() {
    // when
    final var rejection =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .eventBasedGateway()
                    .intermediateCatchEvent("timer-catch-attached")
                    .timerWithDuration(Duration.ofMinutes(10))
                    .endEvent()
                    .moveToLastGateway()
                    .intermediateCatchEvent()
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
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
            elements currently: ['timer-catch-attached' of type 'TIMER' 'INTERMEDIATE_CATCH_EVENT']. \
            See https://github.com/camunda/zeebe/issues/12653 for more details.
            """
                .formatted(processId));
  }

  @Test
  public void shouldRejectDeployProcessWithTimerEventSubProcessEventForSpecificTenant() {
    // when
    final var rejection =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .eventSubProcess(
                        "timer-sub",
                        sub ->
                            sub.startEvent("timer-start-event-sub")
                                .timerWithDuration(Duration.ofMinutes(10))
                                .endEvent())
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
            elements currently: ['timer-start-event-sub' of type 'TIMER' 'START_EVENT']. \
            See https://github.com/camunda/zeebe/issues/12653 for more details.
            """
                .formatted(processId));
  }

  @Test
  public void shouldRejectDeployProcessWithTimerBoundaryEventForSpecificTenant() {
    // when
    final var rejection =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .manualTask()
                    .boundaryEvent("timer-boundary")
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
            elements currently: ['timer-boundary' of type 'TIMER' 'BOUNDARY_EVENT']. \
            See https://github.com/camunda/zeebe/issues/12653 for more details.
            """
                .formatted(processId));
  }
}
