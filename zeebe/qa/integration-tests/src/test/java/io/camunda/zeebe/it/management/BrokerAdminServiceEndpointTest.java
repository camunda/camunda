/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.management;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class BrokerAdminServiceEndpointTest {

  static RequestSpecification brokerServerSpec;

  @TestZeebe
  private static TestStandaloneBroker broker =
      new TestStandaloneBroker().withProperty("management.server.base-path", "/foo");

  private static final String EXPECTED_PARTITIONS_JSON =
      // language=JSON
      """
      {
        "1": {
          "role": "LEADER",
          "processedPosition": -1,
          "snapshotId": null,
          "processedPositionInSnapshot": null,
          "streamProcessorPhase": "PROCESSING",
          "exporterPhase": "EXPORTING",
          "exportedPosition": -1,
          "clock": {
            "instant": "2024-10-29T07:10:02.688Z",
            "modificationType": "None",
            "modification": {}
          },
          "health": {
            "id": "Partition-1",
            "name": "Partition-1",
            "status": "HEALTHY",
            "componentsState": "HEALTHY",
            "children": [
                {
                 "id": "SnapshotDirector-1",
                 "name": "SnapshotDirector-1",
                 "status": "HEALTHY",
                 "children": []
               },
               {
                 "id": "ZeebePartitionHealth-1",
                 "name": "ZeebePartitionHealth-1",
                 "status": "HEALTHY",
                 "children": []
               },
               {
                 "id": "StreamProcessor-1",
                 "name": "StreamProcessor-1",
                 "status": "HEALTHY",
                 "children": []
               },
               {
                 "id": "Exporter-1",
                 "name": "Exporter-1",
                 "status": "HEALTHY",
                 "children": []
               },
               {
                 "id": "RaftPartition-1",
                 "name": "RaftPartition-1",
                 "status": "HEALTHY",
                 "children": []
               }
             ]
           }
        }
      }
      """;
  private static final String EXPECTED_PARTITION_JSON =
      // language=JSON
      """
      {
        "role": "LEADER",
        "processedPosition": -1,
        "snapshotId": null,
        "processedPositionInSnapshot": null,
        "streamProcessorPhase": "PROCESSING",
        "exporterPhase": "EXPORTING",
        "exportedPosition": -1,
        "clock": {
          "instant": "2024-10-29T07:24:41.576Z",
          "modificationType": "None",
          "modification": {}
        },
        "health": {
             "id": "Partition-1",
             "name": "Partition-1",
             "status": "HEALTHY",
             "componentsState": "HEALTHY",
             "children": [
               {
                 "id": "SnapshotDirector-1",
                 "name": "SnapshotDirector-1",
                 "status": "HEALTHY",
                 "children": []
               },
               {
                 "id": "ZeebePartitionHealth-1",
                 "name": "ZeebePartitionHealth-1",
                 "status": "HEALTHY",
                 "children": []
               },
               {
                 "id": "StreamProcessor-1",
                 "name": "StreamProcessor-1",
                 "status": "HEALTHY",
                 "children": []
               },
               {
                 "id": "Exporter-1",
                 "name": "Exporter-1",
                 "status": "HEALTHY",
                 "children": []
               },
               {
                 "id": "RaftPartition-1",
                 "name": "RaftPartition-1",
                 "status": "HEALTHY",
                 "children": []
               }
             ]
           }
      }
      """;

  private static String sanitizeJson(final String json) {
    final var timestampPattern =
        Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
    final var timestampReplacement = "2024-10-29T07:10:02.688Z";
    return json
        // map timestamp into the same value
        .replaceAll(timestampPattern.pattern(), timestampReplacement)
        // remove all whitespaces from the pretty printing
        .replaceAll("\\s", "");
  }

  @BeforeAll
  static void setUpClass() {
    brokerServerSpec =
        new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            // set URL explicitly since we want to ensure the mapping is correct
            .setBaseUri("http://localhost:" + broker.mappedPort(TestZeebePort.MONITORING) + "/foo")
            .addFilter(new ResponseLoggingFilter())
            .addFilter(new RequestLoggingFilter())
            .build();
  }

  @Test
  void shouldReturnPartitions() {
    await("Partitions up")
        .atMost(60, TimeUnit.SECONDS)
        .until(
            () -> {
              final var response = given().spec(brokerServerSpec).when().get("actuator/partitions");
              final var bodyString = new String(response.body().asByteArray());
              assertThat(sanitizeJson(bodyString))
                  .isEqualTo(sanitizeJson(EXPECTED_PARTITIONS_JSON));
              return response.statusCode() == 200;
            });
    await("Single partitions route is up")
        .atMost(5, TimeUnit.SECONDS)
        .until(
            () -> {
              final var response =
                  given().spec(brokerServerSpec).when().get("actuator/partitions/1");

              final var bodyString = new String(response.body().asByteArray());
              assertThat(sanitizeJson(bodyString)).isEqualTo(sanitizeJson(EXPECTED_PARTITION_JSON));
              return response.statusCode() == 200;
            });
  }
}
