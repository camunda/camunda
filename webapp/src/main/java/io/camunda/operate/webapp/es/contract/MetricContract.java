/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.es.contract;

import io.camunda.operate.webapp.management.dto.UsageMetricDTO;

import java.time.OffsetDateTime;

public interface MetricContract {

  interface Reader {
    UsageMetricDTO retrieveProcessInstanceCount(OffsetDateTime startDate, OffsetDateTime endDate);
  }

  interface Writer {
    void registerProcessInstanceCompleteEvent(String processInstanceKey);
  }
}
