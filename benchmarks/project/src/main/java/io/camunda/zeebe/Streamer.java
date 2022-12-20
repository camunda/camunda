/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe;

import io.camunda.zeebe.Worker.DelayedCommand;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.impl.Loggers;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.WorkerCfg;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Future;
import org.slf4j.LoggerFactory;

public class Streamer extends App {
  private static final Counter RECEIVED_JOBS =
      Counter.build()
          .namespace("zeebe_job_stream")
          .name("client_received_job")
          .help("Total count of received jobs for a specific run")
          .register();
  private static final Histogram JOB_PROCESS_LATENCY =
      Histogram.build()
          .namespace("zeebe_job_stream")
          .name("client_job_process_latency")
          .help("Time it takes to to 'process' a job, including the delay and the completion time")
          .register();

  private final AppCfg appCfg;

  Streamer(final AppCfg appCfg) {
    this.appCfg = appCfg;
  }

  @Override
  public void run() {
    final WorkerCfg workerCfg = appCfg.getWorker();
    final long completionDelay = workerCfg.getCompletionDelay().toMillis();
    final var variables = readVariables(workerCfg.getPayloadPath());
    final BlockingQueue<Future<?>> requestFutures = new ArrayBlockingQueue<>(10_000);
    final DelayQueue<DelayedCommand> delayedCommands = new DelayQueue<>();

    final ZeebeClient client = createZeebeClient();
    printTopology(client);
    final var streamer =
        client
            .newStreamJobsCommand()
            .handler(
                (jobClient, job) -> {
                  RECEIVED_JOBS.inc();

                  // it's important to complete async as otherwise the stream gets waaay behind
                  final var timer = JOB_PROCESS_LATENCY.startTimer();
                  final var command =
                      jobClient.newCompleteCommand(job.getKey()).variables(variables);
                  Loggers.JOB_WORKER_LOGGER.error("Adding a delayed command");
                  delayedCommands.offer(
                      new DelayedCommand(
                          Instant.now().plusMillis(completionDelay), command, timer));
                })
            .send();

    final ResponseChecker responseChecker = new ResponseChecker(requestFutures);
    responseChecker.start();

    final var asyncJobCompleter = new DelayedCommandSender(delayedCommands, requestFutures);
    asyncJobCompleter.start();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  LoggerFactory.getLogger(getClass()).warn("Shutting down");
                  streamer.cancel(true);
                  client.close();
                  asyncJobCompleter.close();
                  responseChecker.close();
                }));

    streamer.join();
  }

  private ZeebeClient createZeebeClient() {
    final WorkerCfg workerCfg = appCfg.getWorker();
    final ZeebeClientBuilder builder =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(appCfg.getBrokerUrl())
            .numJobWorkerExecutionThreads(workerCfg.getThreads())
            .defaultJobWorkerName(workerCfg.getWorkerName())
            .defaultJobTimeout(workerCfg.getCompletionDelay().multipliedBy(6))
            .defaultJobWorkerMaxJobsActive(workerCfg.getCapacity())
            .defaultJobPollInterval(workerCfg.getPollingDelay())
            .withProperties(System.getProperties())
            .withInterceptors(monitoringInterceptor);

    if (!appCfg.isTls()) {
      builder.usePlaintext();
    }

    return builder.build();
  }

  public static void main(final String[] args) {
    createApp(Streamer::new);
  }
}
