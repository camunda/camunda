/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@EnabledIfSystemProperty(named = "camunda.docker.test.enabled", matches = "true")
public class StartCamundaDockerIT extends AbstractCamundaDockerIT {
  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @AutoClose private CloseableHttpClient httpClient = HttpClients.createDefault();

  @Test
  public void testStartCamundaDocker() throws Exception {
    // given
    // create and start Elasticsearch container
    final ElasticsearchContainer elasticsearchContainer =
        createContainer(this::createElasticsearchContainer);
    elasticsearchContainer.start();
    // create camunda container
    final GenericContainer camundaContainer = createContainer(this::createCamundaContainer);

    // when
    startContainer(camundaContainer);
    // then - verify overall health endpoint
    assertHealthEndpoint(
        camundaContainer,
        "/actuator/health",
        """
            {
              "status": "UP",
              "components": {
                "brokerReady": {"status": "UP"},
                "brokerStartup": {"status": "UP"},
                "brokerStatus": {
                  "status": "UP",
                  "details": {
                    "Partition-default-1": {
                      "id": "Partition-default-1",
                      "name": "Partition-default-1",
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
                          "id": "MigrationSnapshotDirector",
                          "name": "MigrationSnapshotDirector",
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
                },
                "livenessState": {"status": "UP"},
                "nodeIdProvider":{"status":"UP"},
                "nodeIdProviderReady":{"status":"UP"},
                "readinessState": {"status": "UP"},
                "schemaReadinessCheck":{"status":"UP"}
              },
              "groups": ["liveness", "readiness", "startup", "status"]
            }
            """);

    // verify readiness probe
    assertHealthEndpoint(
        camundaContainer,
        "/actuator/health/readiness",
        """
            {
              "status": "UP",
              "components": {
                "brokerReady": {"status": "UP"},
                "nodeIdProviderReady": {"status": "UP"},
                "readinessState": {"status": "UP"},
                "schemaReadinessCheck":{"status":"UP"}
              }
            }
            """);

    // verify liveness probe
    assertHealthEndpoint(
        camundaContainer,
        "/actuator/health/liveness",
        """
            {
              "status": "UP",
              "components": {
                "brokerReady": {"status": "UP"},
                "nodeIdProviderReady": {"status": "UP"}
              }
            }
            """);

    // verify startup probe
    assertHealthEndpoint(
        camundaContainer,
        "/actuator/health/startup",
        """
            {"status": "UP"}
            """);
  }

  private static String healthUrl(final GenericContainer<?> container, final String path) {
    return String.format(
        "http://%s:%d%s", container.getHost(), container.getMappedPort(MANAGEMENT_PORT), path);
  }

  private void assertHealthEndpoint(
      final GenericContainer<?> container, final String path, final String expectedJson)
      throws IOException, ParseException {
    try (final var response = httpClient.execute(new HttpGet(healthUrl(container, path)))) {
      assertThat(response.getCode()).isEqualTo(200);
      final var actual = OBJECT_MAPPER.readTree(EntityUtils.toString(response.getEntity()));
      final var expected = OBJECT_MAPPER.readTree(expectedJson);
      assertThat(actual).isEqualTo(expected);
    }
  }
}
