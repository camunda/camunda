/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.metrics;

/**
 * Default implementation for MetricsRecorder simply ignoring the counts. Typically, you will
 * replace this by a proper Micrometer implementation as you can find in the starter module
 * (activated if Actuator is on the classpath)
 */
public class DefaultNoopMetricsRecorder implements MetricsRecorder {

  @Override
  public void increase(
      final String metricName, final String action, final String type, final int count) {
    // ignore
  }

  @Override
  public void executeWithTimer(
      final String metricName, final String jobType, final Runnable methodToExecute) {
    methodToExecute.run();
  }
}
