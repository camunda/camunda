/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.webapp.security.OperateURIs;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class MetricAssert {

//  public static void assertThatMetricsAreDisabledFrom(MockMvc mockMvc) {
//    MockHttpServletRequestBuilder request = get(ENDPOINT);
//    try {
//      mockMvc.perform(request)
//          .andExpect(status().is(404));
//    } catch (Exception e) {
//      throw new RuntimeException("Exception while asserting:" + e.getMessage(), e);
//    }
//  }
  
  public static void assertThatMetricsFrom(MockMvc mockMvc, Matcher<? super String> matcher) {
    MockHttpServletRequestBuilder request = get("/actuator/prometheus");
    try {
      mockMvc.perform(request)
          .andExpect(status().isOk())
          .andExpect(content().string(matcher));
    } catch (Exception e) {
      throw new RuntimeException("Exception while asserting:" + e.getMessage(), e);
    }
  }

  public static class ValueMatcher extends BaseMatcher {

    private final String metricName;
    private final Predicate<Double> valueMatcher;

    public ValueMatcher(String metricName, Predicate<Double> valueMatcher) {
      this.metricName = metricName.toLowerCase();
      this.valueMatcher = valueMatcher;
    }

    @Override
    public boolean matches(Object o) {
      Double metricValue = getMetricValue(o);
      if (metricValue != null) {
        return valueMatcher.test(metricValue);
      }
      return false;
    }

    public Double getMetricValue(Object o) {
      Optional<String> metricString = getMetricString(o);
      if (metricString.isPresent()) {
        String[] oneMetric = metricString.get().split(" ");
        if (oneMetric.length > 1) {
          return Double.valueOf(oneMetric[1]);
        }
      }
      return null;
    }

    public Optional<String> getMetricString(Object o) {
      String s = (String)o;
      String[] strings = s.split("\\n");
      return Arrays.stream(strings).filter(str -> str.toLowerCase().contains(metricName) && !str.startsWith("#")).findFirst();
    }

    @Override
    public void describeTo(Description description) {

    }
  }

}
