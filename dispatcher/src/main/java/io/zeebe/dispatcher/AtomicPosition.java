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
package io.zeebe.dispatcher;

import java.util.concurrent.atomic.AtomicLong;

public class AtomicPosition {
  private final AtomicLong position;

  public AtomicPosition() {
    this.position = new AtomicLong(0);
  }

  public void reset() {
    set(-1);
  }

  public long get() {
    return position.get();
  }

  public void set(final long value) {
    position.set(value);
  }

  public boolean proposeMaxOrdered(long newValue) {
    boolean updated = false;

    while (!updated) {
      final long currentPosition = position.get();
      if (currentPosition < newValue) {
        updated = position.compareAndSet(currentPosition, newValue);
      } else {
        return false;
      }
    }

    return updated;
  }
}
