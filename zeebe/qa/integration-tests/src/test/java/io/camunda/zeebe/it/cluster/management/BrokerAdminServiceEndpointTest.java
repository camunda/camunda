/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.management;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.broker.system.management.HealthTree;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.health.HealthStatus;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class BrokerAdminServiceEndpointTest {

  static RequestSpecification brokerServerSpec;
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new Jdk8Module()).registerModule(new JavaTimeModule());
  private static final Duration ENDPOINT_TIMEOUT = Duration.ofSeconds(60);
  private static final String PARTITION_HEALTH_ID = "Partition-default-1";
  private static final List<String> EXPECTED_HEALTH_CHILDREN =
      List.of(
          "SnapshotDirector-1",
          "ZeebePartitionHealth-1",
          "StreamProcessor-1",
          "Exporter-1",
          "MigrationSnapshotDirector",
          "RaftPartition-1");

  @TestZeebe
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withProperty("management.server.base-path", "/foo");

  @BeforeAll
  static void setUpClass() {
    brokerServerSpec =
        new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            // set URL explicitly since we want to ensure the mapping is correct
            .setBaseUri("http://localhost:" + BROKER.mappedPort(TestZeebePort.MONITORING) + "/foo")
            .addFilter(new ResponseLoggingFilter())
            .addFilter(new RequestLoggingFilter())
            .build();
  }

  @Test
  void shouldReturnPartitions() {
    // given - the broker is started by the test extension

    // when + then
    await("partitions route is up")
        .atMost(ENDPOINT_TIMEOUT)
        .untilAsserted(
            () ->
                assertPartitionsResponse(
                    given().spec(brokerServerSpec).when().get("actuator/partitions")));

    await("single partition route is up")
        .atMost(ENDPOINT_TIMEOUT)
        .untilAsserted(
            () ->
                assertPartitionResponse(
                    given().spec(brokerServerSpec).when().get("actuator/partitions/1")));
  }

  private static void assertPartitionsResponse(final Response response) {
    assertThat(response.statusCode()).isEqualTo(200);

    final var partitions = readPartitions(response);
    assertThat(partitions).containsOnlyKeys(1);
    assertPartitionStatus(partitions.get(1));
  }

  private static void assertPartitionResponse(final Response response) {
    assertThat(response.statusCode()).isEqualTo(200);
    assertPartitionStatus(readPartition(response));
  }

  private static void assertPartitionStatus(final PartitionStatusResponse partition) {
    assertThat(partition).isNotNull();
    assertThat(partition.role()).isEqualTo("LEADER");
    assertThat(partition.snapshotId()).matches("\\d+-\\d+-\\d+-\\d+-\\d+-\\w+");
    assertThat(partition.processedPosition()).isNotNull().isGreaterThanOrEqualTo(0L);
    assertThat(partition.processedPositionInSnapshot()).isNotNull().isGreaterThanOrEqualTo(0L);
    assertThat(partition.processedPosition())
        .isGreaterThanOrEqualTo(partition.processedPositionInSnapshot());
    assertThat(partition.streamProcessorPhase()).isEqualTo("PROCESSING");
    assertThat(partition.exporterPhase()).isEqualTo("EXPORTING");
    assertThat(partition.exportedPosition()).isEqualTo(-1L);
    assertThat(partition.clock()).isNotNull();
    assertThat(partition.clock().instant()).isNotNull();
    assertThat(partition.clock().modificationType()).isEqualTo("None");
    assertThat(partition.clock().modification()).isEmpty();
    assertPartitionHealth(partition.health());
  }

  private static void assertPartitionHealth(final HealthTree health) {
    assertThat(health.id()).isEqualTo(PARTITION_HEALTH_ID);
    assertThat(health.name()).isEqualTo(PARTITION_HEALTH_ID);
    assertThat(health.status()).isEqualTo(HealthStatus.HEALTHY);
    assertThat(health.message()).isEmpty();
    assertThat(health.since()).isEmpty();
    assertThat(health.componentsState()).hasValue(HealthStatus.HEALTHY);
    assertThat(health.children()).hasSize(EXPECTED_HEALTH_CHILDREN.size());
    assertThat(health.children())
        .extracting(HealthTree::id)
        .containsExactlyInAnyOrderElementsOf(EXPECTED_HEALTH_CHILDREN);
    assertThat(health.children())
        .extracting(HealthTree::name)
        .containsExactlyInAnyOrderElementsOf(EXPECTED_HEALTH_CHILDREN);
    assertThat(health.children())
        .allSatisfy(
            child -> {
              assertThat(child.status()).isEqualTo(HealthStatus.HEALTHY);
              assertThat(child.message()).isEmpty();
              assertThat(child.since()).isEmpty();
              assertThat(child.componentsState()).isEmpty();
              assertThat(child.children()).isEmpty();
            });
  }

  private static Map<Integer, PartitionStatusResponse> readPartitions(final Response response) {
    try {
      return MAPPER.readValue(
          response.body().asByteArray(),
          new TypeReference<Map<Integer, PartitionStatusResponse>>() {});
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static PartitionStatusResponse readPartition(final Response response) {
    try {
      return MAPPER.readValue(response.body().asByteArray(), PartitionStatusResponse.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PartitionStatusResponse(
      String role,
      Long processedPosition,
      String snapshotId,
      Long processedPositionInSnapshot,
      String streamProcessorPhase,
      String exporterPhase,
      Long exportedPosition,
      ClockStatusResponse clock,
      HealthTree health) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ClockStatusResponse(
      Instant instant, String modificationType, Map<String, Object> modification) {}
}
