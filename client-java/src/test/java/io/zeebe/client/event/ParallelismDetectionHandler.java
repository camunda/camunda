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
package io.zeebe.client.event;

import io.zeebe.client.api.record.Record;
import io.zeebe.client.api.subscription.RecordHandler;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelismDetectionHandler implements RecordHandler {

  protected AtomicBoolean executing = new AtomicBoolean(false);
  protected AtomicBoolean parallelInvocationDetected = new AtomicBoolean(false);
  protected AtomicInteger numInvocations = new AtomicInteger(0);
  protected long timeout;

  public ParallelismDetectionHandler(Duration duration) {
    this.timeout = duration.toMillis();
  }

  @Override
  public void onRecord(Record record) throws Exception {
    numInvocations.incrementAndGet();
    if (executing.compareAndSet(false, true)) {
      try {
        Thread.sleep(timeout);
      } finally {
        executing.set(false);
      }
    } else {
      parallelInvocationDetected.set(true);
    }
  }

  public boolean hasDetectedParallelism() {
    return parallelInvocationDetected.get();
  }

  public int numInvocations() {
    return numInvocations.get();
  }
}
