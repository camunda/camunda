/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.management;

import io.camunda.operate.es.contract.MetricContract;
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

  @Autowired private MetricContract.Reader reader;

  /**
   * Retrieve total of started instances given a period of time
   *
   * <p>Sample Usage:
   * <HOST>:<PORT>/actuator/usage-metrics/process-instances?startTime=2012-12-19T06:01:17.171Z&endTime=2012-12-29T06:01:17.171Z
   *
   */
  @GetMapping(
      value = "/process-instances",
      produces = {MediaType.APPLICATION_JSON_VALUE})
  public UsageMetricDTO retrieveProcessInstanceCount(UsageMetricQueryDTO query) {
    Long total = reader.retrieveProcessInstanceCount(query.getStartTime(), query.getEndTime());
    return new UsageMetricDTO().setTotal(total);
  }
  /**
   * Retrieve total of decision instances given a period of time
   *
   * <p>Sample Usage:
   * <HOST>:<PORT>/actuator/usage-metrics/decision-instances?startTime=2012-12-19T06:01:17.171Z&endTime=2012-12-29T06:01:17.171Z
   *
   */
  @GetMapping(
      value = "/decision-instances",
      produces = {MediaType.APPLICATION_JSON_VALUE})
  public UsageMetricDTO retrieveDecisionInstancesCount(UsageMetricQueryDTO query) {
    Long total = reader.retrieveDecisionInstanceCount(query.getStartTime(), query.getEndTime());
    return new UsageMetricDTO().setTotal(total);
  }
}
