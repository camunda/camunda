/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe;

import io.camunda.zeebe.Worker.DelayedCommand;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.WorkerCfg;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Future;

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
            .jobType(workerCfg.getJobType())
            .handler(
                (jobClient, job) -> {
                  RECEIVED_JOBS.inc();

                  // it's important to complete async as otherwise the stream gets waaay behind
                  final var timer = JOB_PROCESS_LATENCY.startTimer();
                  final var command =
                      jobClient.newCompleteCommand(job.getKey()).variables(variables);
                  delayedCommands.offer(
                      new DelayedCommand(
                          Instant.now().plusMillis(completionDelay), command, timer));
                })
            .workerName(workerCfg.getWorkerName())
            .timeout(Duration.ofSeconds(10))
            .send();

    final ResponseChecker responseChecker = new ResponseChecker(requestFutures);
    responseChecker.start();

    final var asyncJobCompleter = new DelayedCommandSender(delayedCommands, requestFutures);
    asyncJobCompleter.start();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
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
