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
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.WorkerCfg;
import io.prometheus.client.Counter;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.LoggerFactory;

public class Streamer extends App {
  private static final Counter RECEIVED_JOBS =
      Counter.build()
          .namespace("job_stream")
          .name("client_received_job")
          .help("Total count of received jobs for a specific run")
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
    final BlockingDeque<DelayedCommand> delayedCommands = new LinkedBlockingDeque<>(10_000);
    final ShutdownSignalBarrier shutdownBarrier = new ShutdownSignalBarrier();

    final ZeebeClient client = createZeebeClient();
    printTopology(client);
    final var streamer =
        client
            .newStreamJobsCommand()
            .handler(
                (jobClient, job) -> {
                  RECEIVED_JOBS.inc();

                  // it's important to complete async as otherwise the stream gets waaay behind
                  final var command =
                      jobClient.newCompleteCommand(job.getKey()).variables(variables);
                  delayedCommands.addLast(
                      new DelayedCommand(Instant.now().plusMillis(completionDelay), command));
                })
            .send();

    final ResponseChecker responseChecker = new ResponseChecker(requestFutures);
    responseChecker.start();

    final var asyncJobCompleter = new DelayedCommandSender(delayedCommands, requestFutures);
    if (workerCfg.isCompleteJobsAsync()) {
      asyncJobCompleter.start();
    }

    LoggerFactory.getLogger(getClass()).warn("Waiting for shutdown signal...");
    shutdownBarrier.await();
    LoggerFactory.getLogger(getClass()).warn("Shutting down...");
    streamer.cancel(true);
    client.close();
    asyncJobCompleter.close();
    responseChecker.close();
    stopMonitoringServer();
    LoggerFactory.getLogger(getClass()).warn("Shutdown");
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
