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
package io.zeebe.util.sched.clock;

/**
 * Default actor clock implementation; minimizes calls to {@link System#currentTimeMillis()} to once
 * per millisecond.
 */
public class DefaultActorClock implements ActorClock {
  long timeMillis;

  long nanoTime;

  long nanoTimeOfLastMilli;

  long nanosSinceLastMilli;

  @Override
  public boolean update() {
    updateNanos();

    if (nanosSinceLastMilli >= 1_000_000) {
      timeMillis = System.currentTimeMillis();
      nanoTimeOfLastMilli = nanoTime;
      return true;
    }

    return false;
  }

  private void updateNanos() {
    nanoTime = System.nanoTime();
    nanosSinceLastMilli = nanoTime - nanoTimeOfLastMilli;
  }

  @Override
  public long getTimeMillis() {
    return timeMillis;
  }

  @Override
  public long getNanosSinceLastMillisecond() {
    return nanosSinceLastMilli;
  }

  @Override
  public long getNanoTime() {
    return nanoTime;
  }
}
