/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class MetricAssert {
  
  public static final String ENDPOINT = "/actuator/prometheus";
  
  public static void assertThatMetricsAreDisabledFrom(MockMvc mockMvc) {
    MockHttpServletRequestBuilder request = get(ENDPOINT);
    try {
      mockMvc.perform(request)
          .andExpect(status().is(404));
    } catch (Exception e) {
      throw new RuntimeException("Exception while asserting:" + e.getMessage(), e);
    }
  }
  
  public static void assertThatMetricsFrom(MockMvc mockMvc, Matcher<? super String> matcher) {
    MockHttpServletRequestBuilder request = get(ENDPOINT);
    try {
      mockMvc.perform(request).andExpect(status().isOk())
        .andExpect(content().string(matcher));
    } catch (Exception e) {
      throw new RuntimeException("Exception while asserting:" + e.getMessage(), e);
    }
  }

  public static class MetricsMatcher extends BaseMatcher {

    private final String metricName;
    private final Predicate<Double> countMatcher;

    public MetricsMatcher(String metricName, Predicate<Double> countMatcher) {
      this.metricName = metricName.toLowerCase();
      this.countMatcher = countMatcher;
    }

    @Override
    public boolean matches(Object o) {
      String s = (String) o;
      String[] strings = s.split("\\n");
      Optional<String> first = Arrays.stream(strings).filter(str -> str.toLowerCase().contains(metricName) && !str.startsWith("#")).findFirst();
      if (first.isPresent()) {
        String[] oneMetric = first.get().split(" ");
        if (oneMetric.length > 1) {
          return countMatcher.test(Double.valueOf(oneMetric[1]));
        }
      }
      return false;
    }

    @Override
    public void describeTo(Description description) {

    }
  }

}
