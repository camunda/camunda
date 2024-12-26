/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.metrics;

public interface MetricsRecorder {

  String METRIC_NAME_JOB = "camunda.job.invocations";
  String ACTION_ACTIVATED = "activated";
  String ACTION_COMPLETED = "completed";
  String ACTION_FAILED = "failed";
  String ACTION_BPMN_ERROR = "bpmn-error";

  /**
   * Increase the counter for the given metric name, action and type
   *
   * @param metricName - the name of the metric
   * @param action - event type within the metric, e.g. activated, completed, failed, bpmn-error
   * @param type - type of the job the metric is for
   */
  default void increase(final String metricName, final String action, final String type) {
    increase(metricName, action, type, 1);
  }

  /**
   * Increase the counter for the given metric name, action and type
   *
   * @param metricName - the name of the metric
   * @param action - event type within the metric, e.g. activated, completed, failed, bpmn-error
   * @param type - type of the job the metric is for
   * @param count - the amount to increase the metric by
   */
  void increase(String metricName, String action, String type, int count);

  /**
   * Execute the given runnable and measure the execution time
   *
   * <p>Note: the provided runnable is executed synchronously
   *
   * @param metricName - the name of the metric
   * @param jobType - type of the job the metric is for
   * @param methodToExecute - the method to execute
   */
  void executeWithTimer(String metricName, String jobType, Runnable methodToExecute);
}
