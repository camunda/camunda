/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.smoke;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;

@ZeebeIntegration
final class StandaloneBrokerIT {

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker().withUnauthenticatedAccess().withRecordingExporter(true);

  @AutoClose private CamundaClient client;

  @BeforeEach
  void beforeEach() {
    client = broker.newClientBuilder().build();

    Awaitility.await()
        .timeout(Duration.ofSeconds(10))
        .until(
            () ->
                RecordingExporter.usageMetricsRecords(UsageMetricIntent.EXPORTED)
                    .withEventType(EventType.NONE)
                    .exists());
  }

  /** A simple smoke test to ensure the broker starts and can perform basic functionality. */
  @SmokeTest
  void smokeTest() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var process = Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
    final var partitionActuatorSpec =
        new RequestSpecBuilder()
            .setPort(broker.mappedPort(TestZeebePort.MONITORING))
            .setBasePath("/actuator/partitions")
            .build();

    // when
    final var result = executeProcessInstance(processId, process);
    takeSnapshot(partitionActuatorSpec);

    // then
    assertThat(result.getBpmnProcessId()).isEqualTo(processId);
    await("until there is a snapshot available")
        .until(() -> getLatestSnapshotId(partitionActuatorSpec), Objects::nonNull);
  }

  private ProcessInstanceResult executeProcessInstance(
      final String processId, final BpmnModelInstance process) {
    client.newDeployResourceCommand().addProcessModel(process, processId + ".bpmn").send().join();
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .withResult()
        .send()
        .join();
  }

  private void takeSnapshot(final RequestSpecification partitionActuatorSpec) {
    given()
        .spec(partitionActuatorSpec)
        .contentType(ContentType.JSON)
        .when()
        .post("takeSnapshot")
        .then()
        .statusCode(200);
  }

  private String getLatestSnapshotId(final RequestSpecification partitionActuatorSpec) {
    return given()
        .spec(partitionActuatorSpec)
        .when()
        .get()
        .then()
        .assertThat()
        .statusCode(200)
        .extract()
        .path("1.snapshotId");
  }
}
