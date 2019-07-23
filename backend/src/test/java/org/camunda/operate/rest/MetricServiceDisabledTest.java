/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest;

import org.camunda.operate.Metrics;
import org.camunda.operate.TestApplication;
import org.camunda.operate.util.MetricAssert;
import org.camunda.operate.util.OperateIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {"management.metrics.export.prometheus.enabled = false"}
)
public class MetricServiceDisabledTest extends OperateIntegrationTest{
  
  @Autowired
  Metrics metrics;

  @Test
  public void testThatEndpointIsAccessibleAndMetricsNotEnabled() throws Exception {
    // Given property : management.metrics.export.prometheus.enabled = false
    // When try to retrieve metrics form metrics endpoint
    // Then
    MetricAssert.assertThatMetricsAreDisabledFrom(mockMvc);
  } 
}
