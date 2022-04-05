/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.metric;

import io.camunda.operate.es.contract.MetricContract;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.DependencyInjectionTestExecutionListener;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.webapp.management.dto.UsageMetricDTO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;

import static io.camunda.operate.util.OperateIntegrationTest.DEFAULT_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplication.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
        OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
        "management.endpoints.web.exposure.include = usage-metrics"
    })
@TestExecutionListeners(listeners = DependencyInjectionTestExecutionListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@WithMockUser(DEFAULT_USER)
public class UsageMetricIT {

  public static final String PROCESS_INSTANCE_METRIC_ENDPOINT =
      "/actuator/usage-metrics/process-instances?startTime={startTime}&endTime={endTime}";
  public static final String DECISION_EVALUATION_METRIC_ENDPOINT =
      "/actuator/usage-metrics/decision-instances?startTime={startTime}&endTime={endTime}";

  @Autowired private TestRestTemplate testRestTemplate;
  @MockBean private MetricContract.Reader reader;

  @Test
  public void validateProcessInstanceActuatorEndpointRegistered() {
    when(reader.retrieveProcessInstanceCount(any(), any())).thenReturn(3L);

    final Map<String, String> parameters = new HashMap<>();
    parameters.put("startTime", "1970-11-14T10:50:26.963-0100");
    parameters.put("endTime", "1970-11-14T10:50:26.963-0100");
    final ResponseEntity<UsageMetricDTO> response =
        testRestTemplate.getForEntity(PROCESS_INSTANCE_METRIC_ENDPOINT, UsageMetricDTO.class, parameters);

    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody().getTotal()).isEqualTo(3L);
  }

  @Test
  public void validateDecisionInstanceActuatorEndpointRegistered() {
    when(reader.retrieveDecisionInstanceCount(any(), any())).thenReturn(4L);

    final Map<String, String> parameters = new HashMap<>();
    parameters.put("startTime", "1970-11-14T10:50:26.963-0100");
    parameters.put("endTime", "1970-11-14T10:50:26.963-0100");
    final ResponseEntity<UsageMetricDTO> response =
        testRestTemplate.getForEntity(DECISION_EVALUATION_METRIC_ENDPOINT, UsageMetricDTO.class, parameters);

    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody().getTotal()).isEqualTo(4L);
  }

}
