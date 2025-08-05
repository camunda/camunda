/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.search.query.UsageMetricsTUQuery;
import io.camunda.security.auth.SecurityContext;

public interface UsageMetricsSearchClient {

  UsageMetricsSearchClient withSecurityContext(SecurityContext securityContext);

  UsageMetricStatisticsEntity usageMetricStatistics(final UsageMetricsQuery query);

  UsageMetricTUStatisticsEntity usageMetricTUStatistics(final UsageMetricsTUQuery query);
}
