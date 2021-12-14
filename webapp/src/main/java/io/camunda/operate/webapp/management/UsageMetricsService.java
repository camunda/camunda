/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.management;

import io.camunda.operate.webapp.management.dto.UsageMetricDTO;
import io.camunda.operate.webapp.management.dto.UsageMetricQueryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

@Component
@RestControllerEndpoint(id = "usage-metrics")
public class UsageMetricsService {

  /**
   * Retrieve list of unique assigned users in a given period
   *
   * <p>Sample Usage:
   * <HOST>:<PORT>/actuator/usage-metrics/process-instances?startTime=2012-12-19T06:01:17.171Z&endTime=2012-12-29T06:01:17.171Z
   *
   * <p>TODO: Return UsageMetricDTO as a response - For now this is just the initial setup
   */
  @GetMapping(
      value = "/process-instances",
      produces = {MediaType.APPLICATION_JSON_VALUE})
  public UsageMetricDTO retrieveUniqueAssignedUsers(UsageMetricQueryDTO query) {
    return new UsageMetricDTO().setTotal(99);
  }
}
