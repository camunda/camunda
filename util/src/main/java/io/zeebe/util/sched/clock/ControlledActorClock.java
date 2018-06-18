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

import java.time.Duration;
import java.time.Instant;

/** For testcases */
public class ControlledActorClock implements ActorClock {
  private volatile long currentTime;
  private volatile long currentOffset;

  public ControlledActorClock() {
    reset();
  }

  public void setCurrentTime(long currentTime) {
    this.currentTime = currentTime;
  }

  public void setCurrentTime(Instant currentTime) {
    this.currentTime = currentTime.toEpochMilli();
  }

  public void pinCurrentTime() {
    setCurrentTime(getCurrentTime());
  }

  public void addTime(Duration durationToAdd) {
    if (usesPointInTime()) {
      currentTime += durationToAdd.toMillis();
    } else {
      currentOffset += durationToAdd.toMillis();
    }
  }

  public void reset() {
    currentTime = -1;
    currentOffset = 0;
  }

  @Override
  public long getTimeMillis() {
    if (usesPointInTime()) {
      return currentTime;
    } else {
      long now = System.currentTimeMillis();
      if (usesOffset()) {
        now = now + currentOffset;
      }
      return now;
    }
  }

  public Instant getCurrentTime() {
    return Instant.ofEpochMilli(getTimeMillis());
  }

  protected boolean usesPointInTime() {
    return currentTime > 0;
  }

  protected boolean usesOffset() {
    return currentOffset > 0;
  }

  @Override
  public boolean update() {
    return true;
  }

  @Override
  public long getNanosSinceLastMillisecond() {
    return 0;
  }

  @Override
  public long getNanoTime() {
    return 0;
  }

  public long getCurrentTimeInMillis() {
    return getTimeMillis();
  }
}
