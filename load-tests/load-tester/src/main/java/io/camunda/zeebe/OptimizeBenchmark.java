/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.OptimizeCfg;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptimizeBenchmark extends App {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeBenchmark.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final OptimizeCfg optimizeCfg;
  private final HttpClient httpClient;

  OptimizeBenchmark(final AppCfg config) {
    super(config);
    this.optimizeCfg = config.getOptimize();
    this.httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
  }

  @Override
  public void run() {
    if (optimizeCfg == null || optimizeCfg.getReportPaths() == null) {
      LOG.warn("Optimize Benchmark is not configured, skipping.");
      return;
    }

    final List<String> reportPaths = optimizeCfg.getReportPaths();
    final String baseUrl = optimizeCfg.getBaseUrl();
    final Duration interval = optimizeCfg.getInterval();

    LOG.info("Starting Optimize Benchmark with interval {} and reports {}", interval, reportPaths);

    final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    executorService.scheduleAtFixedRate(
        () -> {
          for (final String reportPath : reportPaths) {
            try {
              final String content = readVariables(reportPath);
              final JsonNode node = MAPPER.readTree(content);
              final JsonNode reportNode;
              if (node.isArray()) {
                reportNode = node.get(0);
              } else {
                reportNode = node;
              }
              evaluateReport(baseUrl, MAPPER.writeValueAsString(reportNode), reportPath);
            } catch (final Exception e) {
              LOG.error("Failed to evaluate report {}", reportPath, e);
            }
          }
        },
        0,
        interval.toMillis(),
        TimeUnit.MILLISECONDS);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  LOG.info("Shutting down Optimize Benchmark");
                  executorService.shutdown();
                  try {
                    if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                      executorService.shutdownNow();
                    }
                  } catch (final InterruptedException e) {
                    executorService.shutdownNow();
                  }
                }));
  }

  private void evaluateReport(
      final String baseUrl, final String reportJson, final String reportName) {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/report/evaluate"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(reportJson))
            .build();

    LOG.debug("Evaluating report {}", reportName);
    final Timer.Sample sample = Timer.start(registry);
    try {
      final HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        LOG.warn(
            "Failed to evaluate report {}: status code {}, body: {}",
            reportName,
            response.statusCode(),
            response.body());
      } else {
        LOG.debug("Successfully evaluated report {}", reportName);
      }
    } catch (final Exception e) {
      LOG.error("Error sending evaluation request for report {}", reportName, e);
    } finally {
      sample.stop(
          Timer.builder("optimize.report.latency.client")
              .description("Latency of report evaluation measured from the benchmark client")
              .tag("report", reportName)
              .register(registry));
    }
  }

  public static void main(final String[] args) {
    createApp(OptimizeBenchmark::new);
  }
}
