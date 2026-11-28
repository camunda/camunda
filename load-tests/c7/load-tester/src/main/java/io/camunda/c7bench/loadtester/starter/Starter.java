/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c7bench.loadtester.starter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.c7bench.loadtester.LoadTesterProperties;
import io.camunda.c7bench.loadtester.common.PayloadReader;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Starts process instances at a fixed rate via {@code POST
 * /process-definition/key/{key}/start}.
 *
 * <p>Metric names follow the C8 load tester so dashboards and reports can be compared:
 * {@code starter_process_instances_started_total{outcome,error}}, {@code
 * starter_process_instances_submissions_total{outcome}}, {@code starter_response_latency_seconds},
 * {@code starter_process_instances_in_flight}.
 */
@Component
@Profile("starter")
public class Starter {

  private static final Logger LOG = LoggerFactory.getLogger(Starter.class);

  private final LoadTesterProperties.Starter cfg;
  private final String restUrl;
  private final MeterRegistry registry;
  private final ExecutorService responseExecutor;
  private final HttpClient http;
  private final AtomicInteger inFlight = new AtomicInteger();
  private final AtomicInteger runFinished = new AtomicInteger();
  private final Counter sent;
  private final Counter skipped;
  private final Timer responseLatency;
  private volatile boolean running = true;
  private Thread loop;

  public Starter(final LoadTesterProperties props, final MeterRegistry registry) {
    cfg = props.starter();
    restUrl = props.restUrl();
    this.registry = registry;
    responseExecutor = Executors.newFixedThreadPool(cfg.threads());
    http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .executor(responseExecutor)
            .build();

    Gauge.builder("client.info", () -> 1)
        .tag("name", "starter")
        .tag("process_id", cfg.processId())
        .tag("nb_threads", String.valueOf(cfg.threads()))
        .register(registry);
    Gauge.builder("starter.run.finished", runFinished, AtomicInteger::get).register(registry);
    Gauge.builder("starter.process.instances.in.flight", inFlight, AtomicInteger::get)
        .description("Start requests sent and still awaiting an answer")
        .register(registry);
    sent = submissions("sent");
    skipped = submissions("skipped");
    responseLatency =
        Timer.builder("starter.response.latency")
            .description("Time from sending the start request to receiving the response")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(60))
            .register(registry);
  }

  private Counter submissions(final String outcome) {
    return Counter.builder("starter.process.instances.submissions")
        .tag("outcome", outcome)
        .register(registry);
  }

  private void answered(final String outcome, final String error) {
    Counter.builder("starter.process.instances.started")
        .description("Answered start requests by outcome and error type")
        .tag("outcome", outcome)
        .tag("error", error)
        .register(registry)
        .increment();
  }

  @EventListener(ApplicationReadyEvent.class)
  void start() {
    String bpmn = PayloadReader.readResource(cfg.bpmnXmlPath());
    if (cfg.historyTimeToLive() != null && !cfg.historyTimeToLive().isBlank()) {
      bpmn =
          bpmn.replaceAll(
              "camunda:historyTimeToLive=\"[^\"]*\"",
              "camunda:historyTimeToLive=\"" + cfg.historyTimeToLive() + "\"");
    }
    final String fileName = cfg.bpmnXmlPath().substring(cfg.bpmnXmlPath().lastIndexOf('/') + 1);
    ProcessDeployer.deploy(http, restUrl, fileName, bpmn);

    final ObjectNode body = PayloadReader.mapper().createObjectNode();
    body.set("variables", PayloadReader.toRestVariables(PayloadReader.readResource(cfg.payloadPath())));
    final byte[] bodyBytes = body.toString().getBytes();
    final URI uri = URI.create(restUrl + "/process-definition/key/" + cfg.processId() + "/start");

    loop = Thread.ofPlatform().name("starter-loop").daemon().start(() -> run(uri, bodyBytes));
  }

  /** Paces requests against wall-clock time, so a slow tick does not lower the rate. */
  private void run(final URI uri, final byte[] body) {
    final double ratePerNano = cfg.ratePerSecond() / 1_000_000_000d;
    final int maxInFlight = cfg.maxInFlight();
    final long t0 = System.nanoTime();
    final long deadline =
        cfg.durationLimit() > 0 ? t0 + Duration.ofSeconds(cfg.durationLimit()).toNanos() : Long.MAX_VALUE;
    long scheduled = 0;
    LOG.info(
        "Starting {} instances/s of '{}' (max in flight {})",
        cfg.ratePerSecond(),
        cfg.processId(),
        maxInFlight);

    while (running) {
      final long now = System.nanoTime();
      if (now >= deadline) {
        LOG.info("Duration limit reached, stopping");
        runFinished.set(1);
        return;
      }
      final long due = (long) ((now - t0) * ratePerNano);
      for (; scheduled < due; scheduled++) {
        if (inFlight.get() >= maxInFlight) {
          skipped.increment();
          continue;
        }
        send(uri, body);
      }
      LockSupport.parkNanos(1_000_000);
    }
  }

  private void send(final URI uri, final byte[] body) {
    final HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(cfg.requestTimeout())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
    inFlight.incrementAndGet();
    sent.increment();
    final long start = System.nanoTime();
    http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
        .whenComplete(
            (response, error) -> {
              inFlight.decrementAndGet();
              responseLatency.record(Duration.ofNanos(System.nanoTime() - start));
              if (error != null) {
                answered("failure", error.getCause() instanceof HttpTimeoutException
                    || error instanceof HttpTimeoutException ? "timeout" : "io");
              } else if (response.statusCode() == 200) {
                answered("success", "none");
              } else {
                answered("failure", "http_" + response.statusCode());
              }
            });
  }

  @PreDestroy
  void stop() {
    running = false;
    if (loop != null) {
      loop.interrupt();
    }
    responseExecutor.shutdown();
  }
}
