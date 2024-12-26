/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.metrics;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Super simple class to record metrics in memory. Typically used for test cases */
public class SimpleMetricsRecorder implements MetricsRecorder {

  public HashMap<String, AtomicLong> counters = new HashMap<>();

  public HashMap<String, Long> timers = new HashMap<>();

  @Override
  public void increase(
      final String metricName, final String action, final String type, final int count) {
    final String key = key(metricName, action, type);
    if (!counters.containsKey(key)) {
      counters.put(key, new AtomicLong(count));
    } else {
      counters.get(key).addAndGet(count);
    }
  }

  @Override
  public void executeWithTimer(
      final String metricName, final String jobType, final Runnable methodToExecute) {
    final long startTime = System.currentTimeMillis();
    methodToExecute.run();
    timers.put(metricName + "#" + jobType, System.currentTimeMillis() - startTime);
  }

  private String key(final String metricName, final String action, final String type) {
    final String key = metricName + "#" + action + "#" + type;
    return key;
  }

  public long getCount(final String metricName, final String action, final String type) {
    if (!counters.containsKey(key(metricName, action, type))) {
      return 0;
    }
    return counters.get(key(metricName, action, type)).get();
  }
}
