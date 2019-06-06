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
package io.zeebe.distributedlog.restore.log.impl;

import io.zeebe.distributedlog.restore.log.LogReplicationAppender;
import java.util.ArrayList;
import java.util.List;

public class RecordingLogReplicationAppender implements LogReplicationAppender {
  private final List<Invocation> invocations = new ArrayList<>();

  public List<Invocation> getInvocations() {
    return invocations;
  }

  @Override
  public long append(long commitPosition, byte[] blockBuffer) {
    invocations.add(new Invocation(commitPosition, blockBuffer));
    return 1; // always return success
  }

  public void reset() {
    invocations.clear();
  }

  public static class Invocation {
    final long commitPosition;
    final byte[] serializedEvents;

    public Invocation(long commitPosition, byte[] serializedEvents) {
      this.commitPosition = commitPosition;
      this.serializedEvents = serializedEvents;
    }
  }
}
