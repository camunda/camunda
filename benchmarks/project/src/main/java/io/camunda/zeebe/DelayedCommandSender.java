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

import io.zeebe.Worker.DelayedCommand;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;

public class DelayedCommandSender extends Thread {

  private volatile boolean shuttingDown = false;
  private final BlockingDeque<DelayedCommand> commands;
  private final BlockingQueue<Future<?>> requestFutures;

  public DelayedCommandSender(
      final BlockingDeque<DelayedCommand> delayedCommands,
      final BlockingQueue<Future<?>> requestFutures) {
    this.commands = delayedCommands;
    this.requestFutures = requestFutures;
  }

  @Override
  public void run() {
    while (!shuttingDown) {
      try {
        final var delayedCommand = commands.takeFirst();
        if (!delayedCommand.hasExpired()) {
          commands.addFirst(delayedCommand);
        } else {
          requestFutures.add(delayedCommand.getCommand().send());
        }
      } catch (InterruptedException e) {
        // ignore and retry
      }
    }
  }

  public void close() {
    shuttingDown = true;
    interrupt();
  }
}
