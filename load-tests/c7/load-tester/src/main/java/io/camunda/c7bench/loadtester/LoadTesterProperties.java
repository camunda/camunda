/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c7bench.loadtester;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("load-tester")
public record LoadTesterProperties(String engineUrl, Starter starter, Worker worker) {

  /** REST base URL, e.g. http://engine:8080/engine-rest. */
  public String restUrl() {
    return engineUrl.replaceAll("/+$", "") + "/engine-rest";
  }

  public record Starter(
      String processId,
      /* Process instances per rateDuration. */
      int rate,
      Duration rateDuration,
      /* Threads handling HTTP responses. */
      int threads,
      String bpmnXmlPath,
      String payloadPath,
      /* Overrides camunda:historyTimeToLive in the BPMN when set. */
      String historyTimeToLive,
      /* open: up to 10 * rate/s requests in flight. closed: up to 1 * rate/s. */
      String workloadModel,
      Duration requestTimeout,
      /* Seconds; <= 0 runs forever. */
      int durationLimit) {

    public double ratePerSecond() {
      return rate * 1_000_000_000d / rateDuration.toNanos();
    }

    public int maxInFlight() {
      final double factor = "closed".equalsIgnoreCase(workloadModel) ? 1 : 10;
      return (int) Math.max(1, Math.ceil(ratePerSecond() * factor));
    }
  }

  public record Worker(
      String topic,
      String workerName,
      /* Tasks per fetch-and-lock request. */
      int maxTasks,
      /* Max tasks being handled at once per worker (C8: max-jobs-active). */
      int capacity,
      /* Threads completing tasks. */
      int threads,
      Duration completionDelay,
      Duration lockDuration,
      /* Long polling timeout for fetch-and-lock. */
      Duration asyncResponseTimeout,
      String payloadPath,
      boolean completeWithPayload,
      boolean fetchVariables) {}
}
