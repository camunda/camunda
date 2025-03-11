/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.metric;

import static io.camunda.operate.util.OperateAbstractIT.DEFAULT_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.qa.util.DependencyInjectionTestExecutionListener;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.webapp.management.dto.UsageMetricDTO;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplication.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "management.endpoints.web.exposure.include = usage-metrics",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      "spring.profiles.active=test,consolidated-auth"
    })
@TestExecutionListeners(
    listeners = DependencyInjectionTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@WithMockUser(DEFAULT_USER)
public class UsageMetricIT {

  public static final String PROCESS_INSTANCE_METRIC_ENDPOINT =
      "/actuator/usage-metrics/process-instances?startTime={startTime}&endTime={endTime}";
  public static final String DECISION_EVALUATION_METRIC_ENDPOINT =
      "/actuator/usage-metrics/decision-instances?startTime={startTime}&endTime={endTime}";

  @Autowired private TestRestTemplate testRestTemplate;
  @MockBean private MetricsStore metricsStore;

  @LocalManagementPort private int managementPort;

  @Test
  public void validateProcessInstanceActuatorEndpointRegistered() {
    when(metricsStore.retrieveProcessInstanceCount(any(), any())).thenReturn(3L);

    final Map<String, String> parameters = new HashMap<>();
    parameters.put("startTime", "1970-11-14T10:50:26.963-0100");
    parameters.put("endTime", "1970-11-14T10:50:26.963-0100");
    final ResponseEntity<UsageMetricDTO> response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + PROCESS_INSTANCE_METRIC_ENDPOINT,
            UsageMetricDTO.class,
            parameters);

    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody().getTotal()).isEqualTo(3L);
  }

  @Test
  public void validateDecisionInstanceActuatorEndpointRegistered() {
    when(metricsStore.retrieveDecisionInstanceCount(any(), any())).thenReturn(4L);

    final Map<String, String> parameters = new HashMap<>();
    parameters.put("startTime", "1970-11-14T10:50:26.963-0100");
    parameters.put("endTime", "1970-11-14T10:50:26.963-0100");
    final ResponseEntity<UsageMetricDTO> response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + DECISION_EVALUATION_METRIC_ENDPOINT,
            UsageMetricDTO.class,
            parameters);

    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody().getTotal()).isEqualTo(4L);
  }
}
