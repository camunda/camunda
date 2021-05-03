/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.HashMap;
import java.util.Map;

public final class ProcessInstances {
  public static Map<String, String> getCurrentVariables(final long processInstanceKey) {
    final Record<ProcessInstanceRecordValue> completed =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withFlowScopeKey(-1)
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();

    return getCurrentVariables(processInstanceKey, -1, completed.getPosition());
  }

  public static Map<String, String> getCurrentVariables(
      final long processInstanceKey, final long completedPosition) {
    return getCurrentVariables(processInstanceKey, -1, completedPosition);
  }

  /**
   * CAVEAT: as this has no knowledge of the scope hierarchy, it just returns a map-reduce of all
   * the variable records for the given process instance. This means, for example, that two tasks
   * running in parallel in different sub-processes would see their respective scopes.
   */
  public static Map<String, String> getCurrentVariables(
      final long processInstanceKey, final long startingPosition, final long stoppingPosition) {
    final Map<String, String> document = new HashMap<>();
    final int partitionId = Protocol.decodePartitionId(processInstanceKey);

    return RecordingExporter.records()
        .withPartitionId(partitionId)
        .between(startingPosition, stoppingPosition)
        .variableRecords()
        .withProcessInstanceKey(processInstanceKey)
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
