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
package io.zeebe.test.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Not-thread-safe utility to take timestamps and print a summary of time elapsed between
 * timestamps. Useful for analyzing where tests spend runtime.
 */
public class Stopwatch {
  protected List<Checkpoint> checkpoints = new ArrayList<>();

  public void record(String checkpoint) {
    System.out.println(checkpoint);
    final Checkpoint c = new Checkpoint();
    c.name = checkpoint;
    c.timestamp = System.currentTimeMillis();
    checkpoints.add(c);
  }

  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("\nStopwatch results:\n");

    if (checkpoints.size() >= 2) {
      for (int i = 1; i < checkpoints.size(); i++) {
        final Checkpoint from = checkpoints.get(i - 1);
        final Checkpoint to = checkpoints.get(i);

        sb.append("From ");
        sb.append(from.name);
        sb.append(" to ");
        sb.append(to.name);
        sb.append(": ");
        sb.append(Duration.ofMillis(to.timestamp - from.timestamp));
        sb.append("\n");
      }
    } else {
      sb.append("Needs at least two checkpoints");
    }

    sb.append("\n");

    return sb.toString();
  }

  protected static class Checkpoint {
    String name;
    long timestamp;
  }
}
