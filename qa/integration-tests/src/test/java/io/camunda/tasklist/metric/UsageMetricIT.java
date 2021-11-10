/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.metric;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.graphql.mutation.TaskMutationResolver;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

public class UsageMetricIT extends TasklistZeebeIntegrationTest {

  public static final String ASSIGNEE_ENDPOINT =
      "/actuator/usage-metrics/assignees?startTime={startTime}&endTime={endTime}";

  @Autowired private TaskMutationResolver taskMutationResolver;
  @Autowired private TestRestTemplate testRestTemplate;

  @Before
  public void before() {
    super.before();
    taskMutationResolver.setZeebeClient(super.getClient());
  }

  @Test
  public void validateActuatorEndpointRegistered() {
    final Map<String, String> parameters = new HashMap<>();
    parameters.put("startTime", "2012-12-19T06:01:17.171Z");
    parameters.put("endTime", "2012-12-29T06:01:17.171Z");
    final ResponseEntity<TemporaryMetricResponseDTO> response =
        testRestTemplate.getForEntity(
            ASSIGNEE_ENDPOINT, TemporaryMetricResponseDTO.class, parameters);

    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody().startTime).isEqualTo(1355896877171L);
    assertThat(response.getBody().endTime).isEqualTo(1356760877171L);
  }

  /** This class is temporary and it is here exclusively for the initial setup */
  private static class TemporaryMetricResponseDTO {
    private Long startTime;
    private Long endTime;

    public Long getStartTime() {
      return startTime;
    }

    public void setStartTime(Long startTime) {
      this.startTime = startTime;
    }

    public Long getEndTime() {
      return endTime;
    }

    public void setEndTime(Long endTime) {
      this.endTime = endTime;
    }
  }
}
