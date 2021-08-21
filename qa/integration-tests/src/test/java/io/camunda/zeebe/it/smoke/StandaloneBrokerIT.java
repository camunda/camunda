/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.smoke;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.broker.StandaloneBroker;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = StandaloneBroker.class)
@ContextConfiguration(
    initializers = {RandomPortInitializer.class, CollectorRegistryInitializer.class})
@ActiveProfiles("test")
final class StandaloneBrokerIT {

  @SuppressWarnings("unused")
  @Autowired
  private BrokerCfg config;

  @BeforeEach
  void beforeEach() {
    await("until broker is ready").untilAsserted(this::assertBrokerIsReady);
  }

  /** A simple smoke test to ensure the broker starts and can perform basic functionality. */
  @SmokeTest
  void smokeTest() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var process = Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
    final var partitionActuatorSpec =
        new RequestSpecBuilder()
            .setPort(config.getNetwork().getMonitoringApi().getPort())
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
      final String processId, final io.camunda.zeebe.model.bpmn.BpmnModelInstance process) {
    try (final var client = createClient()) {
      await("until topology is complete").untilAsserted(() -> assertTopologyIsComplete(client));

      client.newDeployCommand().addProcessModel(process, processId).send().join();
      return client
          .newCreateInstanceCommand()
          .bpmnProcessId(processId)
          .latestVersion()
          .withResult()
          .send()
          .join();
    }
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

  private void assertBrokerIsReady() {
    given()
        .port(config.getNetwork().getMonitoringApi().getPort())
        .when()
        .get("/ready")
        .then()
        .statusCode(204);
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

  private void assertTopologyIsComplete(final ZeebeClient client) {
    TopologyAssert.assertThat(client.newTopologyRequest().send().join()).isComplete(1, 1);
  }

  private ZeebeClient createClient() {
    return ZeebeClient.newClientBuilder()
        .usePlaintext()
        .gatewayAddress("localhost:" + config.getGateway().getNetwork().getPort())
        .build();
  }
}
