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
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@EnabledIfSystemProperty(named = "camunda.docker.test.enabled", matches = "true")
public class StartCamundaDockerIT extends AbstractCamundaDockerIT {
  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    // and when performing health check
    try (final CloseableHttpClient httpClient = HttpClients.createDefault();
        final CloseableHttpResponse healthCheckResponse =
            httpClient.execute(
                new HttpGet(
                    String.format(
                        "http://%s:%d%s",
                        camundaContainer.getHost(),
                        camundaContainer.getMappedPort(MANAGEMENT_PORT),
                        "/actuator/health")))) {

      // then - convert the response and expected response to intermediate JSON representation
      // this will allow us to compare without worrying about the ordering of the values, and just
      // checking that they are logically equivalent
      assertThat(healthCheckResponse.getCode()).isEqualTo(200);
      final String expectedHealthCheckResponse =
          """
              {
                "status": "UP",
                "components": {
                  "brokerReady": {"status": "UP"},
                  "brokerStartup": {"status": "UP"},
                  "brokerStatus": {"status": "UP"},
                  "indicesCheck": {"status": "UP"},
                  "livenessState": {"status": "UP"},
                  "nodeIdProvider":{"status":"UP"},
                  "nodeIdProviderReady":{"status":"UP"},
                  "readinessState": {"status": "UP"},
                  "searchEngineCheck": {"status": "UP"}
                },
                "groups": ["liveness", "readiness", "startup", "status"]
              }
              """;
      final var expectedJson = OBJECT_MAPPER.readTree(expectedHealthCheckResponse);
      final var actualJson =
          OBJECT_MAPPER.readTree(EntityUtils.toString(healthCheckResponse.getEntity()));

      assertThat(actualJson).isEqualTo(expectedJson);
    }
  }
}
