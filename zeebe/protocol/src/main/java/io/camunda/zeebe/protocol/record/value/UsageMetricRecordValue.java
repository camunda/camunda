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
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableUsageMetricRecordValue.Builder.class)
public interface UsageMetricRecordValue extends RecordValue {

  UsageMetricEvent getEvent();

  long getStartTime();

  long getEndTime();

  Map<String, List<Long>> getValue();

  enum UsageMetricEvent {
    EVENT_PROCESS_INSTANCE_FINISHED("EVENT_PROCESS_INSTANCE_FINISHED"),
    EVENT_PROCESS_INSTANCE_STARTED("EVENT_PROCESS_INSTANCE_STARTED"),
    EVENT_DECISION_INSTANCE_EVALUATED("EVENT_DECISION_INSTANCE_EVALUATED"),
    EVENT_TASK_COMPLETED_BY_ASSIGNEE("task_completed_by_assignee");

    private final String value;

    UsageMetricEvent(final String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }

    public static UsageMetricEvent from(final String value) {
      return Arrays.stream(values()).filter(e -> e.value.equals(value)).findFirst().orElse(null);
    }
  }
}
