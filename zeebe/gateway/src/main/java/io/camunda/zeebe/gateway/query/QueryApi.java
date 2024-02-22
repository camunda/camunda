/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.query;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * The query API allows you to asynchronously retrieve information about certain resources from a
 * remote partition on a broker.
 *
 * <p>NOTE: queries are routed to the correct partition based on the given key. They may also be
 * routed to remote nodes.
 */
public interface QueryApi {

  /**
   * Looks up the process definition from the given key, and returns its BPMN process ID. The key
   * should be the one obtained via the deployment command for the given process.
   *
   * <p>After {@code timeout} duration, the returned future is completed exceptionally with a {@link
   * java.util.concurrent.TimeoutException}.
   *
   * @param key the process definition key
   * @param timeout the maximum duration to wait for until the request is completed
   * @return the process ID of the process definition identified by the given key
   */
  CompletionStage<String> getBpmnProcessIdFromProcess(final long key, final Duration timeout);

  /**
   * Looks up the process instance identified by the given key, and returns the BPMN process ID of
   * its process definition. The key should be the one obtained via the instance creation command.
   *
   * <p>After {@code timeout} duration, the returned future is completed exceptionally with a {@link
   * java.util.concurrent.TimeoutException}.
   *
   * @param key the process instance key
   * @param timeout the maximum duration to wait for until the request is completed
   * @return the process ID associated with the process instance identified by the given key
   */
  CompletionStage<String> getBpmnProcessIdFromProcessInstance(
      final long key, final Duration timeout);

  /**
   * Looks up the job identified by the given key, and returns the BPMN process ID associated with
   * its process instance. The key should be the one obtained one of the job commands, e.g. job
   * activation.
   *
   * <p>After {@code timeout} duration, the returned future is completed exceptionally with a {@link
   * java.util.concurrent.TimeoutException}.
   *
   * @param key the job key
   * @param timeout the maximum duration to wait for until the request is completed
   * @return the process ID associated with the job identified by the given key
   */
  CompletionStage<String> getBpmnProcessIdFromJob(final long key, final Duration timeout);
}
