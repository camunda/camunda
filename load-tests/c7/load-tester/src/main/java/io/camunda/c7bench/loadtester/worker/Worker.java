/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c7bench.loadtester.worker;

import io.camunda.c7bench.loadtester.LoadTesterProperties;
import io.camunda.c7bench.loadtester.common.PayloadReader;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.client.topic.TopicSubscriptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * External task worker built on the official Camunda 7 Java client.
 *
 * <p>The client fetches and locks with long polling (comparable to C8 job streaming) on one
 * thread. Tasks are handed to a pool of {@code threads}; at most {@code capacity} tasks are in
 * progress at once (C8: max-jobs-active). When the worker is at capacity, the fetch thread blocks,
 * so it stops fetching new tasks.
 *
 * <p>Metrics: {@code worker_tasks_received_total}, {@code worker_tasks_completed_total{outcome}},
 * {@code worker_complete_latency_seconds} (REST complete call), {@code
 * worker_task_wait_seconds} (external task created until fetched), {@code
 * worker_tasks_in_progress}.
 */
@Component
@Profile("worker")
public class Worker {

  private static final Logger LOG = LoggerFactory.getLogger(Worker.class);

  private final LoadTesterProperties.Worker cfg;
  private final String restUrl;
  private final Semaphore capacity;
  private final ExecutorService executor;
  private final Map<String, Object> completeVariables;
  private final Counter received;
  private final Counter completed;
  private final Counter failed;
  private final Timer completeLatency;
  private final Timer taskWait;
  private ExternalTaskClient client;

  public Worker(final LoadTesterProperties props, final MeterRegistry registry) {
    cfg = props.worker();
    restUrl = props.restUrl();
    capacity = new Semaphore(cfg.capacity());
    executor = Executors.newFixedThreadPool(cfg.threads());
    completeVariables =
        cfg.completeWithPayload()
            ? PayloadReader.toClientVariables(PayloadReader.readResource(cfg.payloadPath()))
            : Map.of();

    Gauge.builder("client.info", () -> 1)
        .tag("name", "worker")
        .tag("topic", cfg.topic())
        .tag("nb_threads", String.valueOf(cfg.threads()))
        .tag("capacity", String.valueOf(cfg.capacity()))
        .register(registry);
    Gauge.builder("worker.tasks.in.progress", () -> cfg.capacity() - capacity.availablePermits())
        .register(registry);
    received = Counter.builder("worker.tasks.received").register(registry);
    completed = Counter.builder("worker.tasks.completed").tag("outcome", "success").register(registry);
    failed = Counter.builder("worker.tasks.completed").tag("outcome", "failure").register(registry);
    completeLatency =
        Timer.builder("worker.complete.latency")
            .description("Duration of the REST complete call")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(30))
            .register(registry);
    taskWait =
        Timer.builder("worker.task.wait")
            .description("Time from external task creation until the worker received it")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofMinutes(5))
            .register(registry);
  }

  @PostConstruct
  void open() {
    final int connections = cfg.threads() + 2;
    client =
        ExternalTaskClient.create()
            .baseUrl(restUrl)
            .workerId(cfg.workerName() + "-" + System.getenv().getOrDefault("HOSTNAME", "local"))
            .maxTasks(cfg.maxTasks())
            .asyncResponseTimeout(cfg.asyncResponseTimeout().toMillis())
            .lockDuration(cfg.lockDuration().toMillis())
            .disableBackoffStrategy()
            .customizeHttpClient(
                b ->
                    b.setConnectionManager(
                        PoolingHttpClientConnectionManagerBuilder.create()
                            .setMaxConnTotal(connections)
                            .setMaxConnPerRoute(connections)
                            .build()))
            .build();

    TopicSubscriptionBuilder subscription = client.subscribe(cfg.topic()).handler(this::handle);
    if (!cfg.fetchVariables()) {
      subscription = subscription.variables();
    }
    subscription.open();
    LOG.info(
        "Worker subscribed to '{}' at {} (maxTasks={}, capacity={}, threads={}, delay={})",
        cfg.topic(),
        restUrl,
        cfg.maxTasks(),
        cfg.capacity(),
        cfg.threads(),
        cfg.completionDelay());
  }

  /** Runs on the client's fetch thread: blocks when at capacity. */
  private void handle(final ExternalTask task, final ExternalTaskService service) {
    final long receivedAt = System.currentTimeMillis();
    received.increment();
    if (task.getCreateTime() != null) {
      taskWait.record(Duration.ofMillis(Math.max(0, receivedAt - task.getCreateTime().getTime())));
    }
    try {
      capacity.acquire();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }
    executor.execute(() -> complete(task, service, receivedAt));
  }

  private void complete(final ExternalTask task, final ExternalTaskService service, final long receivedAt) {
    try {
      final long remaining = cfg.completionDelay().toMillis() - (System.currentTimeMillis() - receivedAt);
      if (remaining > 0) {
        Thread.sleep(remaining);
      }
      final long start = System.nanoTime();
      service.complete(task, completeVariables);
      completeLatency.record(Duration.ofNanos(System.nanoTime() - start));
      completed.increment();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (final RuntimeException e) {
      failed.increment();
      LOG.warn("Failed to complete task {}: {}", task.getId(), e.getMessage());
    } finally {
      capacity.release();
    }
  }

  @PreDestroy
  void close() {
    if (client != null) {
      client.stop();
    }
    executor.shutdown();
  }
}
