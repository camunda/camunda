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

import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseChecker extends Thread {
  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(Worker.class), Duration.ofSeconds(5));

  private final BlockingQueue<Future<?>> futures;
  private volatile boolean shuttingDown = false;

  public ResponseChecker(final BlockingQueue<Future<?>> futures) {
    this.futures = futures;
  }

  @Override
  public void run() {
    while (!shuttingDown) {
      try {
        futures.take().get();
      } catch (final InterruptedException e) {
        // ignore and retry
      } catch (final ExecutionException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof StatusRuntimeException) {
          final StatusRuntimeException statusRuntimeException = (StatusRuntimeException) cause;
          if (statusRuntimeException.getStatus().getCode() != Code.RESOURCE_EXHAUSTED) {
            // we don't want to flood the log
            THROTTLED_LOGGER.warn("Request failed", e);
          }
        }
      }
    }
  }

  public void close() {
    shuttingDown = true;
    interrupt();
  }
}
