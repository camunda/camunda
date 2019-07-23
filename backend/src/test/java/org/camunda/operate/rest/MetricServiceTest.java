/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest;

import org.camunda.operate.Metrics;

import static org.camunda.operate.util.MetricAssert.assertThatMetricsFrom;
import static org.hamcrest.CoreMatchers.containsString;

import org.camunda.operate.util.OperateIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class MetricServiceTest extends OperateIntegrationTest{
  protected final String testMetric = "testThatEndpointIsAccessible";
  @Autowired
  Metrics metrics;

  @Test
  public void testThatEndpointIsAccessibleAndMetricsEnabled() throws Exception {
    // Given metrics enabled (Properties)
    // when
    metrics.recordCounts("test", 1, "name",testMetric);
    // then
    assertThatMetricsFrom(mockMvc,containsString(testMetric));
  }
   
}
