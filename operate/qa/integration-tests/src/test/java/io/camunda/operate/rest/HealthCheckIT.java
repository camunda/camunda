/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.application.StandaloneOperate;
import io.camunda.operate.management.IndicesHealthIndicator;
import io.camunda.operate.rest.HealthCheckIT.AddManagementPropertiesInitializer;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;

@AutoConfigureMetrics
@AutoConfigureTestRestTemplate
@SpringBootTest(
    classes = {TestApplicationWithNoBeans.class, IndicesHealthIndicator.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = AddManagementPropertiesInitializer.class)
@RunWith(SpringRunner.class)
public class HealthCheckIT {

  @MockitoBean private IndicesHealthIndicator probes;

  @LocalManagementPort private int managementPort;

  @Autowired private TestRestTemplate testRestTemplate;

  @Test
  public void testReady() throws Exception {
    given(probes.health(anyBoolean())).willReturn(Health.up().build());
    final var response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/health/readiness", Map.class);

    assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.SC_OK);
    assertThat(response.getBody()).containsEntry("status", "UP");
    verify(probes, times(1)).health(anyBoolean());
  }

  @Test
  public void testHealth() throws Exception {
    final var response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/health/liveness", Map.class);

    assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.SC_OK);
    assertThat(response.getBody()).containsEntry("status", "UP");
    verifyNoInteractions(probes);
  }

  @Test
  public void testReadyStateIsNotOK() throws Exception {
    given(probes.health(anyBoolean())).willReturn(Health.down().build());

    final var livenessResponse =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/health/liveness", Map.class);

    assertThat(livenessResponse.getStatusCode().value()).isEqualTo(HttpStatus.SC_OK);
    assertThat(livenessResponse.getBody()).containsEntry("status", "UP");

    final var readinessResponse =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/health/readiness", Map.class);

    assertThat(readinessResponse.getStatusCode().value())
        .isEqualTo(HttpStatus.SC_SERVICE_UNAVAILABLE);
    verify(probes, times(2)).health(anyBoolean());
  }

  @Test
  public void testMetrics() throws Exception {
    final var response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/prometheus", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.SC_OK);
    assertThat(response.getBody())
        .contains(
            "# HELP jvm_memory_used_bytes The amount of used memory\n"
                + "# TYPE jvm_memory_used_bytes gauge");
  }

  public static class AddManagementPropertiesInitializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
      final Map<String, Object> map = StandaloneOperate.getManagementProperties();
      final List<String> properties = new ArrayList();
      map.forEach(
          (key, value) -> {
            // not clear how to connect mockMvc to management port
            if (!key.contains("port")) {
              properties.add(key + "=" + value);
            }
          });
      TestPropertyValues.of(properties).applyTo(applicationContext.getEnvironment());
    }
  }
}
