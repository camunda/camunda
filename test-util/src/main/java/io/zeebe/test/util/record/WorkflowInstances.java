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
package io.zeebe.test.util.record;

import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.HashMap;
import java.util.Map;

public class WorkflowInstances {
  public static Map<String, String> getCurrentVariables(long workflowInstanceKey) {
    final Record<WorkflowInstanceRecordValue> completed =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withFlowScopeKey(-1)
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();

    return getCurrentVariables(workflowInstanceKey, -1, completed.getPosition());
  }

  public static Map<String, String> getCurrentVariables(
      long workflowInstanceKey, long completedPosition) {
    return getCurrentVariables(workflowInstanceKey, -1, completedPosition);
  }

  /**
   * CAVEAT: as this has no knowledge of the scope hierarchy, it just returns a map-reduce of all
   * the variable records for the given workflow instance. This means, for example, that two tasks
   * running in parallel in different sub-processes would see their respective scopes.
   */
  public static Map<String, String> getCurrentVariables(
      long workflowInstanceKey, long startingPosition, long stoppingPosition) {
    final Map<String, String> document = new HashMap<>();
    final int partitionId = Protocol.decodePartitionId(workflowInstanceKey);

    return RecordingExporter.records()
        .withPartitionId(partitionId)
        .between(startingPosition, stoppingPosition)
        .variableRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .map(Record::getValue)
        .reduce(
            document,
            (acc, r) -> {
              acc.put(r.getName(), r.getValue());
              return acc;
            },
            (m, o) -> {
              m.putAll(o);
              return m;
            });
  }
}
