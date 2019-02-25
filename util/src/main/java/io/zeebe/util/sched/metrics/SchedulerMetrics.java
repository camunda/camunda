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
package io.zeebe.util.sched.metrics;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.status.ConcurrentCountersManager;
import org.agrona.concurrent.status.CountersReader.MetaData;

public class SchedulerMetrics {
  public static final String TASK_MAX_EXECUTION_TIME_MICROS_PROP =
      "io.zeebe.scheduler.taskMaxExecutionTimeMicros";
  public static final int TASK_MAX_EXECUTION_TIME_NANOS =
      1000 * Integer.getInteger(TASK_MAX_EXECUTION_TIME_MICROS_PROP, -1);
  public static final boolean SHOULD_ENABLE_JUMBO_TASK_DETECTION =
      TASK_MAX_EXECUTION_TIME_NANOS >= 0;

  public static final int TYPE_TEMPORAL_VALUE = 1;

  public static void printMetrics(ConcurrentCountersManager countersManager, PrintStream ps) {
    countersManager.forEach(
        new MetaData() {
          @Override
          public void accept(int counterId, int typeId, DirectBuffer keyBuffer, String label) {
            long value = countersManager.getCounterValue(counterId);

            switch (typeId) {
              case TYPE_TEMPORAL_VALUE:
                final long hours = TimeUnit.NANOSECONDS.toDays(value);
                value -= TimeUnit.HOURS.toNanos(hours);
                final long minutes = TimeUnit.NANOSECONDS.toMinutes(value);
                value -= TimeUnit.MINUTES.toNanos(minutes);
                final long seconds = TimeUnit.NANOSECONDS.toSeconds(value);
                value -= TimeUnit.SECONDS.toNanos(seconds);
                final long millis = TimeUnit.NANOSECONDS.toMillis(value);
                value -= TimeUnit.MILLISECONDS.toNanos(millis);
                final long micros = TimeUnit.NANOSECONDS.toMicros(value);
                ps.format(
                    "%s:\t %dh %dm %02ds %03dms %03dμs%n",
                    label, hours, minutes, seconds, millis, micros);
                break;

              default:
                ps.format("%s: %d%n", label, value);
                break;
            }
          }
        });
  }
}
