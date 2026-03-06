/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.system;

import static org.mockito.Mockito.when;

import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration.JobMetricsConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(SystemConfigurationController.class)
public class SystemConfigurationControllerTest extends RestControllerTest {

  static final String JOB_METRICS_CONFIG_URL = "/v2/system/configuration/job-metrics";

  @MockitoBean GatewayRestConfiguration gatewayRestConfiguration;

  @Test
  void shouldReturnDefaultJobMetricsConfiguration() {
    // given
    final var jobMetricsCfg = new JobMetricsConfiguration();
    when(gatewayRestConfiguration.getJobMetrics()).thenReturn(jobMetricsCfg);

    // when/then
    webClient
        .get()
        .uri(JOB_METRICS_CONFIG_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            """
            {
              "enabled": true,
              "exportInterval": "PT5M",
              "maxWorkerNameLength": 100,
              "maxJobTypeLength": 100,
              "maxTenantIdLength": 30,
              "maxUniqueKeys": 9500
            }
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldReturnCustomJobMetricsConfiguration() {
    // given
    final var jobMetricsCfg = new JobMetricsConfiguration();
    jobMetricsCfg.setEnabled(false);
    jobMetricsCfg.setExportInterval(Duration.ofMinutes(10));
    jobMetricsCfg.setMaxWorkerNameLength(50);
    jobMetricsCfg.setMaxJobTypeLength(200);
    jobMetricsCfg.setMaxTenantIdLength(15);
    jobMetricsCfg.setMaxUniqueKeys(5000);
    when(gatewayRestConfiguration.getJobMetrics()).thenReturn(jobMetricsCfg);

    // when/then
    webClient
        .get()
        .uri(JOB_METRICS_CONFIG_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            """
            {
              "enabled": false,
              "exportInterval": "PT10M",
              "maxWorkerNameLength": 50,
              "maxJobTypeLength": 200,
              "maxTenantIdLength": 15,
              "maxUniqueKeys": 5000
            }
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldReturnDisabledJobMetricsConfiguration() {
    // given
    final var jobMetricsCfg = new JobMetricsConfiguration();
    jobMetricsCfg.setEnabled(false);
    when(gatewayRestConfiguration.getJobMetrics()).thenReturn(jobMetricsCfg);

    // when/then
    webClient
        .get()
        .uri(JOB_METRICS_CONFIG_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "enabled": false,
              "exportInterval": "PT5M",
              "maxWorkerNameLength": 100,
              "maxJobTypeLength": 100,
              "maxTenantIdLength": 30,
              "maxUniqueKeys": 9500
            }
            """,
            JsonCompareMode.STRICT);
  }
}
