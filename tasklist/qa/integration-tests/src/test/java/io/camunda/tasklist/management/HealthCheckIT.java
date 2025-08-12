/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.tasklist.JacksonConfig;
import io.camunda.tasklist.connect.ElasticsearchConnector;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@AutoConfigureObservability(tracing = false)
@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      SearchEngineHealthIndicator.class,
      ElasticsearchConnector.class,
      JacksonConfig.class,
      TasklistPropertiesOverride.class,
      SearchEngineConnectPropertiesOverride.class,
      UnifiedConfiguration.class,
      UnifiedConfigurationHelper.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"tasklist", "test", "standalone"})
public class HealthCheckIT {

  @Autowired private TestRestTemplate testRestTemplate;

  @MockBean private SearchEngineHealthIndicator probes;

  @LocalManagementPort private int managementPort;

  @Test
  public void testReady() throws Exception {
    given(probes.getHealth(anyBoolean())).willReturn(Health.up().build());
    final var response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/health/readiness", Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("status", "UP");
    verify(probes, times(1)).getHealth(anyBoolean());
  }

  @Test
  public void testHealth() throws Exception {
    final var response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/health/liveness", Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("status", "UP");
    verifyNoInteractions(probes);
  }

  @Test
  public void testReadyStateIsNotOK() throws Exception {
    given(probes.getHealth(anyBoolean())).willReturn(Health.down().build());
    final var livenessResponse =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/health/liveness", Map.class);
    assertThat(livenessResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(livenessResponse.getBody()).containsEntry("status", "UP");

    final var readinessResponse =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/health/readiness", Map.class);
    assertThat(readinessResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    verify(probes, times(2)).getHealth(anyBoolean());
  }

  @Test
  public void testMetrics() throws Exception {
    final var response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/prometheus", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains(
            "# HELP jvm_memory_used_bytes The amount of used memory\n"
                + "# TYPE jvm_memory_used_bytes gauge");
  }
}
