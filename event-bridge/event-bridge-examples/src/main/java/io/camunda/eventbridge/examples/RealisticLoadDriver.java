/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.examples;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.worker.JobWorker;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Drives the "realistic load" benchmark from the reliability tests ({@code
 * docs/testing/reliability-testing.md}): the bank customer-complaint / credit-card-fraud
 * dispute-handling process, which uses call activities, multi-instance, sub-processes and DMN, so a
 * single process instance fans out into ~50 sub-instances and many flow elements. Starts one
 * process instance per second (the benchmark's rate), driving the whole analytics stack with a much
 * richer, realistic shape than the synthetic demo processes.
 *
 * <p>Deploys the whole resource set (main + refunding sub-process + DMN + user-task form),
 * completes every service-task job with a job worker, and completes the "Decide on fraud case"
 * native user task via a background poller so instances progress instead of parking there.
 *
 * <pre>
 *   java io.camunda.eventbridge.examples.RealisticLoadDriver [piPerSecond]
 *   -Dcamunda.rest=http://localhost:8088
 *   -Drealistic.dir=&lt;repo&gt;/load-tests/load-tester/src/main/resources/bpmn/realistic
 * </pre>
 *
 * Runs until interrupted.
 */
public final class RealisticLoadDriver {

  private static final String MAIN_PROCESS = "bankDisputeHandling";
  private static final String DEFAULT_DIR =
      "load-tests/load-tester/src/main/resources/bpmn/realistic";
  private static final String[] RESOURCES = {
    "bankCustomerComplaintDisputeHandling.bpmn",
    "refundingProcess.bpmn",
    "determineFraudRatingConfidence.dmn",
    "decide_on_fraud_case.form",
  };
  // Every service-task job type across the main + refunding process.
  private static final String[] JOB_TYPES = {
    "customer_notification",
    "dispute_process_request_get_vendor_info",
    "dispute_process_request_proof_from_vendor",
    "extract_data_from_document",
    "inform_about_failed_claim",
    "inform_about_successful_claim",
    "refunding",
  };
  private static final int WORKER_THREADS = 128; // multi-instance fans out ~50 jobs per instance

  private RealisticLoadDriver() {}

  public static void main(final String[] args) throws Exception {
    final String restAddress = System.getProperty("camunda.rest", "http://localhost:8088");
    final Path dir = Path.of(System.getProperty("realistic.dir", DEFAULT_DIR));
    final long piPerSecond = args.length > 0 ? Long.parseLong(args[0]) : 1L;
    final long intervalMs = Math.max(1L, 1000L / piPerSecond);

    final CamundaClient client =
        CamundaClient.newClientBuilder()
            .restAddress(URI.create(restAddress))
            .preferRestOverGrpc(true)
            .numJobWorkerExecutionThreads(WORKER_THREADS)
            .build();

    var deploy =
        client.newDeployResourceCommand().addResourceBytes(read(dir, RESOURCES[0]), RESOURCES[0]);
    for (int i = 1; i < RESOURCES.length; i++) {
      deploy = deploy.addResourceBytes(read(dir, RESOURCES[i]), RESOURCES[i]);
    }
    deploy.send().join();
    System.out.println("Deployed realistic resources: " + String.join(", ", RESOURCES));

    final String payload = Files.readString(dir.resolve("realisticPayload.json"));

    final java.util.List<JobWorker> workers = new java.util.ArrayList<>();
    for (final String type : JOB_TYPES) {
      workers.add(
          client
              .newWorker()
              .jobType(type)
              .handler((jobClient, job) -> jobClient.newCompleteCommand(job.getKey()).send().join())
              .name(type + "-worker")
              .maxJobsActive(WORKER_THREADS)
              .timeout(Duration.ofMinutes(5))
              .open());
    }

    // complete the native "Decide on fraud case" user task so instances flow past it
    final ScheduledExecutorService userTasks = Executors.newSingleThreadScheduledExecutor();
    userTasks.scheduleWithFixedDelay(() -> completeOpenUserTasks(client), 1, 1, TimeUnit.SECONDS);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  workers.forEach(JobWorker::close);
                  userTasks.shutdownNow();
                  client.close();
                }));

    System.out.println(
        "Starting " + MAIN_PROCESS + " at " + piPerSecond + " PI/s (Ctrl-C to stop)…");
    long started = 0;
    while (!Thread.currentThread().isInterrupted()) {
      try {
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(MAIN_PROCESS)
            .latestVersion()
            .variables(payload)
            .send()
            .join();
        if (++started % 25 == 0) {
          System.out.println("started " + started + " instances");
        }
      } catch (final Exception e) {
        System.out.println("start failed: " + e.getMessage());
      }
      Thread.sleep(intervalMs);
    }
  }

  private static void completeOpenUserTasks(final CamundaClient client) {
    try {
      final List<io.camunda.client.api.search.response.UserTask> tasks =
          client
              .newUserTaskSearchRequest()
              .filter(f -> f.state(UserTaskState.CREATED))
              .page(p -> p.limit(50))
              .send()
              .join()
              .items();
      for (final var task : tasks) {
        try {
          client.newCompleteUserTaskCommand(task.getUserTaskKey()).send().join();
        } catch (final Exception ignored) {
          // already completed / gone — best effort
        }
      }
    } catch (final Exception e) {
      // search API may briefly lag on a fresh cluster; retry on the next tick
    }
  }

  private static byte[] read(final Path dir, final String name) throws Exception {
    return Files.readAllBytes(dir.resolve(name));
  }
}
