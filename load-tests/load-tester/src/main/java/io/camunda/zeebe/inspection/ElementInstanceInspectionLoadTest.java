/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.inspection;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.ElementInstanceInspection;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("inspection-load-test")
public class ElementInstanceInspectionLoadTest implements CommandLineRunner {

  private static final Logger LOG =
      LoggerFactory.getLogger(ElementInstanceInspectionLoadTest.class);
  private static final String BPMN_PROCESS_ID = "inspection-load-test";
  private static final String JOB_TYPE = "inspection-test-job";
  private static final int TARGET_INSTANCES = 1000;
  private static final int PARALLEL_REQUESTS = 100;

  private final CamundaClient client;
  private final LoadTesterProperties properties;
  private final MeterRegistry registry;
  private final ConnectionMonitor connectionMonitor;
  private ExecutorService executorService;

  private Timer inspectionLatencyTimer;
  private final AtomicInteger completedRequests = new AtomicInteger(0);
  private final AtomicInteger failedRequests = new AtomicInteger(0);

  public ElementInstanceInspectionLoadTest(
      final CamundaClient client,
      final LoadTesterProperties properties,
      final MeterRegistry registry,
      final ConnectionMonitor connectionMonitor) {
    this.client = client;
    this.properties = properties;
    this.registry = registry;
    this.connectionMonitor = connectionMonitor;
  }

  @Override
  public void run(final String... args) throws Exception {
    LOG.info("Starting Element Instance Inspection Load Test");

    // Initialize metrics
    inspectionLatencyTimer =
        Timer.builder("inspection.latency")
            .description("Latency of inspection API calls")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

    // Wait for topology
    connectionMonitor.awaitAndPrintTopology();

    // Deploy process
    deployProcess();

    // Create process instances
    LOG.info("Creating {} process instances...", TARGET_INSTANCES);
    final List<Long> processInstanceKeys = createProcessInstances();
    LOG.info("Created {} process instances", processInstanceKeys.size());

    // Query jobs to get element instance keys
    LOG.info("Querying jobs to extract element instance keys...");
    final List<Long> elementInstanceKeys = queryJobsForElementInstanceKeys();
    LOG.info("Found {} element instance keys", elementInstanceKeys.size());

    // Execute inspection calls in parallel
    LOG.info(
        "Executing {} inspection API calls in parallel (batch size: {})...",
        elementInstanceKeys.size(),
        PARALLEL_REQUESTS);
    executorService = Executors.newFixedThreadPool(PARALLEL_REQUESTS);
    executeInspectionCalls(elementInstanceKeys);

    // Print results
    printResults();

    LOG.info("Element Instance Inspection Load Test completed");
  }

  private void deployProcess() {
    LOG.info("Deploying test process...");
    final String bpmn =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                          targetNamespace="http://camunda.org/schema/1.0/bpmn">
          <bpmn:process id="%s" isExecutable="true">
            <bpmn:startEvent id="start">
              <bpmn:outgoing>flow1</bpmn:outgoing>
            </bpmn:startEvent>
            <bpmn:serviceTask id="task" name="Test Task">
              <bpmn:extensionElements>
                <zeebe:taskDefinition type="%s" />
              </bpmn:extensionElements>
              <bpmn:incoming>flow1</bpmn:incoming>
              <bpmn:outgoing>flow2</bpmn:outgoing>
            </bpmn:serviceTask>
            <bpmn:endEvent id="end">
              <bpmn:incoming>flow2</bpmn:incoming>
            </bpmn:endEvent>
            <bpmn:sequenceFlow id="flow1" sourceRef="start" targetRef="task" />
            <bpmn:sequenceFlow id="flow2" sourceRef="task" targetRef="end" />
          </bpmn:process>
        </bpmn:definitions>
        """
            .formatted(BPMN_PROCESS_ID, JOB_TYPE);

    client
        .newDeployResourceCommand()
        .addResourceStringUtf8(bpmn, BPMN_PROCESS_ID + ".bpmn")
        .send()
        .join();

    LOG.info("Process deployed successfully");
  }

  private List<Long> createProcessInstances() {
    final List<Long> keys = new ArrayList<>();
    for (int i = 0; i < TARGET_INSTANCES; i++) {
      try {
        final ProcessInstanceEvent event =
            client
                .newCreateInstanceCommand()
                .bpmnProcessId(BPMN_PROCESS_ID)
                .latestVersion()
                .send()
                .join();
        keys.add(event.getProcessInstanceKey());

        if ((i + 1) % 100 == 0) {
          LOG.info("Created {} instances so far...", i + 1);
        }
      } catch (final Exception e) {
        LOG.error("Failed to create process instance", e);
      }
    }
    return keys;
  }

  private List<Long> queryJobsForElementInstanceKeys() {
    final List<Long> elementInstanceKeys = new ArrayList<>();
    try {
      final var jobsResponse =
          client
              .newActivateJobsCommand()
              .jobType(JOB_TYPE)
              .maxJobsToActivate(TARGET_INSTANCES)
              .timeout(Duration.ofMinutes(10))
              .send()
              .join();

      for (final ActivatedJob job : jobsResponse.getJobs()) {
        elementInstanceKeys.add(job.getElementInstanceKey());
      }

      LOG.info("Activated {} jobs", jobsResponse.getJobs().size());
    } catch (final Exception e) {
      LOG.error("Failed to activate jobs", e);
    }
    return elementInstanceKeys;
  }

  private void executeInspectionCalls(final List<Long> elementInstanceKeys) {
    final List<CompletableFuture<Void>> futures = new ArrayList<>();

    for (final Long elementInstanceKey : elementInstanceKeys) {
      final CompletableFuture<Void> future =
          CompletableFuture.runAsync(
              () -> {
                final Timer.Sample sample = Timer.start(registry);
                try {
                  final ElementInstanceInspection inspection =
                      client
                          .newElementInstanceInspectionGetRequest(elementInstanceKey)
                          .requestTimeout(Duration.ofSeconds(30))
                          .send()
                          .join();

                  completedRequests.incrementAndGet();
                  sample.stop(inspectionLatencyTimer);

                  final int completed = completedRequests.get();
                  if (completed % 100 == 0) {
                    LOG.info("Completed {} inspection calls...", completed);
                  }
                } catch (final Exception e) {
                  failedRequests.incrementAndGet();
                  sample.stop(inspectionLatencyTimer);
                  LOG.error(
                      "Failed to get inspection for element instance {}", elementInstanceKey, e);
                }
              },
              executorService);

      futures.add(future);
    }

    // Wait for all requests to complete
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
  }

  private void printResults() {
    LOG.info("=== Element Instance Inspection Load Test Results ===");
    LOG.info("Total requests: {}", completedRequests.get() + failedRequests.get());
    LOG.info("Successful requests: {}", completedRequests.get());
    LOG.info("Failed requests: {}", failedRequests.get());
    LOG.info(
        "Success rate: {:.2f}%",
        (completedRequests.get() * 100.0) / (completedRequests.get() + failedRequests.get()));
    LOG.info("Latency p50: {} ms", inspectionLatencyTimer.mean(TimeUnit.MILLISECONDS));
    LOG.info("Latency p95: {} ms", inspectionLatencyTimer.percentile(0.95, TimeUnit.MILLISECONDS));
    LOG.info("Latency p99: {} ms", inspectionLatencyTimer.percentile(0.99, TimeUnit.MILLISECONDS));
    LOG.info("======================================================");
  }

  @PreDestroy
  void shutdown() {
    if (executorService != null) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (final InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }
}
