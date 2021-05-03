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
package io.zeebe;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.api.command.FinalCommandStep;
import io.zeebe.client.api.worker.JobWorker;
import io.zeebe.config.AppCfg;
import io.zeebe.config.WorkerCfg;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;

public class Worker extends App {

  private final AppCfg appCfg;

  Worker(AppCfg appCfg) {
    this.appCfg = appCfg;
  }

  @Override
  public void run() {
    final WorkerCfg workerCfg = appCfg.getWorker();
    final String jobType = workerCfg.getJobType();
    final long completionDelay = workerCfg.getCompletionDelay().toMillis();
    final var variables = readVariables(workerCfg.getPayloadPath());
    final BlockingQueue<Future<?>> requestFutures = new ArrayBlockingQueue<>(10_000);
    final BlockingDeque<DelayedCommand> delayedCommands = new LinkedBlockingDeque<>(10_000);

    final ZeebeClient client = createZeebeClient();
    printTopology(client);

    final JobWorker worker =
        client
            .newWorker()
            .jobType(jobType)
            .handler(
                (jobClient, job) -> {
                  final var command =
                      jobClient.newCompleteCommand(job.getKey()).variables(variables);
                  if (workerCfg.isCompleteJobsAsync()) {
                    delayedCommands.addLast(
                        new DelayedCommand(Instant.now().plusMillis(completionDelay), command));
                  } else {
                    try {
                      Thread.sleep(completionDelay);
                    } catch (Exception e) {
                      e.printStackTrace();
                    }
                    requestFutures.add(command.send());
                  }
                })
            .open();

    final ResponseChecker responseChecker = new ResponseChecker(requestFutures);
    responseChecker.start();

    final var asyncJobCompleter = new DelayedCommandSender(delayedCommands, requestFutures);
    if (workerCfg.isCompleteJobsAsync()) {
      asyncJobCompleter.start();
    }

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  worker.close();
                  client.close();
                  asyncJobCompleter.close();
                  responseChecker.close();
                }));
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

  public static void main(String[] args) {
    createApp(Worker::new);
  }

  static final class DelayedCommand {

    private final Instant expiration;
    private final FinalCommandStep<?> command;

    public DelayedCommand(final Instant expiration, final FinalCommandStep<?> command) {
      this.expiration = expiration;
      this.command = command;
    }

    public boolean hasExpired() {
      return Instant.now().isAfter(expiration);
    }

    public FinalCommandStep<?> getCommand() {
      return command;
    }
  }
}
