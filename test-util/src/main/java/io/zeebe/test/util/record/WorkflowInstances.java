/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.record;

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import java.util.HashMap;
import java.util.Map;

public final class WorkflowInstances {
  public static Map<String, String> getCurrentVariables(final long workflowInstanceKey) {
    final Record<WorkflowInstanceRecordValue> completed =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withFlowScopeKey(-1)
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();

    return getCurrentVariables(workflowInstanceKey, -1, completed.getPosition());
  }

  public static Map<String, String> getCurrentVariables(
      final long workflowInstanceKey, final long completedPosition) {
    return getCurrentVariables(workflowInstanceKey, -1, completedPosition);
  }

  /**
   * CAVEAT: as this has no knowledge of the scope hierarchy, it just returns a map-reduce of all
   * the variable records for the given workflow instance. This means, for example, that two tasks
   * running in parallel in different sub-processes would see their respective scopes.
   */
  public static Map<String, String> getCurrentVariables(
      final long workflowInstanceKey, final long startingPosition, final long stoppingPosition) {
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
