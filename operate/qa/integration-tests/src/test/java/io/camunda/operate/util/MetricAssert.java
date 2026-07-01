/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
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

  public static void assertThatMetricsFrom(
      final MockMvc mockMvc, final Matcher<? super String> matcher) {
    final MockHttpServletRequestBuilder request = get("/actuator/prometheus");
    try {
      mockMvc.perform(request).andExpect(status().isOk()).andExpect(content().string(matcher));
    } catch (final Exception e) {
      throw new RuntimeException("Exception while asserting:" + e.getMessage(), e);
    }
  }

  public static final class ValueMatcher extends BaseMatcher {

    private final String metricName;
    private final Predicate<Double> valueMatcher;
    private final String[] tags;

    public ValueMatcher(final String metricName, final Predicate<Double> valueMatcher) {
      this(metricName, valueMatcher, new String[0]);
    }

    /**
     * @param tags alternating key/value pairs, e.g. {@code "type", "search"} — each pair is matched
     *     as {@code key="value"} in the Prometheus line
     */
    public ValueMatcher(
        final String metricName, final Predicate<Double> valueMatcher, final String... tags) {
      if (tags.length % 2 != 0) {
        throw new IllegalArgumentException("tags must be provided as alternating key/value pairs");
      }
      this.metricName = metricName.toLowerCase();
      this.valueMatcher = valueMatcher;
      this.tags = tags;
    }

    @Override
    public boolean matches(final Object o) {
      final Double metricValue = getMetricValue(o);
      if (metricValue != null) {
        return valueMatcher.test(metricValue);
      }
      return false;
    }

    public Double getMetricValue(final Object o) {
      final Optional<String> metricString = getMetricString(o);
      if (metricString.isPresent()) {
        final String[] oneMetric = metricString.get().split(" ");
        if (oneMetric.length > 1) {
          return Double.valueOf(oneMetric[1]);
        }
      }
      return null;
    }

    public Optional<String> getMetricString(final Object o) {
      final String s = (String) o;
      final String[] strings = s.split("\\n");
      return Arrays.stream(strings)
          .filter(str -> str.toLowerCase().contains(metricName) && !str.startsWith("#"))
          .filter(this::allTagsPresent)
          .findFirst();
    }

    private boolean allTagsPresent(final String line) {
      for (int i = 0; i + 1 < tags.length; i += 2) {
        if (!line.contains(tags[i] + "=\"" + tags[i + 1] + "\"")) {
          return false;
        }
      }
      return true;
    }

    @Override
    public void describeTo(final Description description) {}
  }
}
