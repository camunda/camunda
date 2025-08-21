/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.management;

import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.webapp.management.dto.UsageMetricDTO;
import io.camunda.operate.webapp.management.dto.UsageMetricQueryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @deprecated please use {@link
 *     io.camunda.zeebe.gateway.rest.controller.system.UsageMetricsController}
 */
@Component
@Deprecated(forRemoval = true, since = "8.8")
@RestControllerEndpoint(id = "usage-metrics")
public class UsageMetricsService {

  @Autowired private MetricsStore metricsStore;

  /**
   * Retrieve total of started instances given a period of time
   *
   * <p>Sample Usage:
   * <HOST>:<PORT>/actuator/usage-metrics/process-instances?startTime=2012-12-19T06:01:17.171Z&endTime=2012-12-29T06:01:17.171Z
   */
  @GetMapping(
      value = "/process-instances",
      produces = {MediaType.APPLICATION_JSON_VALUE})
  public UsageMetricDTO retrieveProcessInstanceCount(UsageMetricQueryDTO query) {
    final Long total =
        metricsStore.retrieveProcessInstanceCount(
            query.getStartTime(), query.getEndTime(), query.getTenantId());
    return new UsageMetricDTO().setTotal(total);
  }

  /**
   * Retrieve total of decision instances given a period of time
   *
   * <p>Sample Usage:
   * <HOST>:<PORT>/actuator/usage-metrics/decision-instances?startTime=2012-12-19T06:01:17.171Z&endTime=2012-12-29T06:01:17.171Z
   */
  @GetMapping(
      value = "/decision-instances",
      produces = {MediaType.APPLICATION_JSON_VALUE})
  public UsageMetricDTO retrieveDecisionInstancesCount(UsageMetricQueryDTO query) {
    final Long total =
        metricsStore.retrieveDecisionInstanceCount(
            query.getStartTime(), query.getEndTime(), query.getTenantId());
    return new UsageMetricDTO().setTotal(total);
  }
}
