/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.examples;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Continuously starts process instances of a process that has a service task, and runs a job worker
 * that completes those jobs — so instances actually <em>complete</em> with a non-trivial duration
 * (the worker sleeps a random short while to simulate work). Feeds the analytics pipeline a steady
 * live stream so the dataset/report shows growing counts and real average durations.
 *
 * <pre>
 *   java io.camunda.eventbridge.examples.ContinuousProcessDriver [processId] [intervalMs]
 *   -Dcamunda.rest=http://localhost:8088
 * </pre>
 *
 * Runs until interrupted (Ctrl-C / kill).
 */
public final class ContinuousProcessDriver {

  private static final String JOB_TYPE = "work";

  private ContinuousProcessDriver() {}

  public static void main(final String[] args) throws InterruptedException {
    final var restAddress = System.getProperty("camunda.rest", "http://localhost:8088");
    final var processId = args.length > 0 ? args[0] : "worker-demo";
    final var intervalMs = args.length > 1 ? Long.parseLong(args[1]) : 2000L;

    final CamundaClient client =
        CamundaClient.newClientBuilder()
            .restAddress(URI.create(restAddress))
            .preferRestOverGrpc(true)
            .build();

    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task")
                .zeebeJobType(JOB_TYPE)
                .endEvent()
                .done(),
            processId + ".bpmn")
        .send()
        .join();
    System.out.println("Deployed '" + processId + "' (start -> service task -> end)");

    final JobWorker worker =
        client
            .newWorker()
            .jobType(JOB_TYPE)
            .handler(
                (jobClient, job) -> {
                  // simulate a little work so completed instances have a varied, non-zero duration
                  Thread.sleep(ThreadLocalRandom.current().nextLong(50, 500));
                  jobClient.newCompleteCommand(job.getKey()).send().join();
                })
            .name("demo-worker")
            .timeout(Duration.ofSeconds(30))
            .open();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  worker.close();
                  client.close();
                }));

    System.out.println("Starting instances every " + intervalMs + "ms (Ctrl-C to stop)…");
    long started = 0;
    while (!Thread.currentThread().isInterrupted()) {
      client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
      if (++started % 10 == 0) {
        System.out.println("started " + started + " instances of '" + processId + "'");
      }
      Thread.sleep(intervalMs);
    }
  }
}
