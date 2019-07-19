/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest;

import static org.hamcrest.CoreMatchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.camunda.operate.Metrics;
import org.camunda.operate.util.OperateIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;


public class MetricServiceTest extends OperateIntegrationTest{
  
  @Autowired
  Metrics metrics;
  
  @Test
  public void testThatEndpointIsAccessible() throws Exception {
    metrics.recordCounts("test", 1, "name","testThatEndpointIsAccessible");
    String expectedResponseSubstring = metrics.isEnabled()?"testThatEndpointIsAccessible":"";
    MockHttpServletRequestBuilder request = get("/actuator/prometheus");
    mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(expectedResponseSubstring)));
  }
}
