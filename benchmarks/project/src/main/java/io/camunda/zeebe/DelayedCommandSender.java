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
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.impl.Loggers;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Future;

public class DelayedCommandSender extends Thread {
  private static final Gauge COMMAND_QUEUE_SIZE =
      Gauge.build()
          .namespace("zeebe_job_stream")
          .name("client_command_queue_size")
          .help("Total number of complete commands enqueued")
          .register();

  private static final Histogram JOB_COMPLETE_LATENCY =
      Histogram.build()
          .namespace("zeebe_job_stream")
          .name("client_job_complete_latency")
          .help("Time it takes to send a complete job command and receive the response")
          .register();

  private volatile boolean shuttingDown = false;
  private final DelayQueue<DelayedCommand> commands;
  private final BlockingQueue<Future<?>> requestFutures;

  public DelayedCommandSender(
      final DelayQueue<DelayedCommand> delayedCommands,
      final BlockingQueue<Future<?>> requestFutures) {
    commands = delayedCommands;
    this.requestFutures = requestFutures;
  }

  @Override
  public void run() {
    while (!shuttingDown) {
      try {
        final var delayedCommand = commands.take();
        Loggers.JOB_WORKER_LOGGER.error("Sending command {}", delayedCommand.sequence);
        final ZeebeFuture<?> requestFuture = delayedCommand.getCommand().send();
        final var timer = JOB_COMPLETE_LATENCY.startTimer();
        requestFuture.whenComplete(
            (ok, error) -> {
              delayedCommand.markCompleted();
              timer.close();
            });
        requestFutures.add(requestFuture);
        COMMAND_QUEUE_SIZE.dec();
      } catch (final InterruptedException e) {
        // ignore and retry
      }
    }
  }

  public void close() {
    shuttingDown = true;
    interrupt();
  }
}
