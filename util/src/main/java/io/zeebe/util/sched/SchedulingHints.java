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
package io.zeebe.util.sched;

public class SchedulingHints {

  public static int ioBound() {
    int hints = 0;

    hints = setIoBound(hints);

    return hints;
  }

  public static int cpuBound(ActorPriority priority) {
    int hints = 0;

    hints = setCpuBound(hints);
    hints = setPriority(priority.getPriorityClass(), hints);

    return hints;
  }

  public static int setCpuBound(int hints) {
    return hints & ~1;
  }

  public static int setIoBound(int hints) {
    return hints | 1;
  }

  public static boolean isCpuBound(int hints) {
    return (hints & ~1) == hints;
  }

  public static boolean isIoBound(int hints) {
    return (hints & 1) == hints;
  }

  public static int setPriority(short priority, int hints) {
    return hints | (priority << 1);
  }

  public static short getPriority(int hints) {
    return (short) (hints >> 1);
  }
}
