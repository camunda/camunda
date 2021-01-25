/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class MigratedStreamProcessors {

  private static final List<Intent> MIGRATED_INTENTS =
      List.of(
          WorkflowInstanceIntent.ACTIVATE_ELEMENT,
          WorkflowInstanceIntent.ELEMENT_ACTIVATING,
          WorkflowInstanceIntent.ELEMENT_ACTIVATED,
          IncidentIntent.CREATED);

  private static final List<BpmnElementType> MIGRATED_BPMN_PROCESSORS =
      List.of(BpmnElementType.SERVICE_TASK);

  private static final Function<TypedRecord<?>, Boolean> NOT_MIGRATED = record -> false;
  private static final Function<TypedRecord<?>, Boolean> MIGRATED = record -> true;

  private static final Map<ValueType, Function<TypedRecord<?>, Boolean>> MIGRATED_VALUE_TYPES =
      new EnumMap<>(ValueType.class);

  static {
    MIGRATED_VALUE_TYPES.put(
        ValueType.WORKFLOW_INSTANCE,
        record -> {
          final var recordValue = (WorkflowInstanceRecord) record.getValue();
          final var bpmnElementType = recordValue.getBpmnElementType();
          return MIGRATED_BPMN_PROCESSORS.contains(bpmnElementType);
        });

    MIGRATED_VALUE_TYPES.put(ValueType.INCIDENT, MIGRATED);
  }

  public static boolean isMigrated(final TypedRecord<?> record) {
    final var intent = record.getIntent();
    final var valueType = record.getValueType();

    return MIGRATED_INTENTS.contains(intent)
        && MIGRATED_VALUE_TYPES.getOrDefault(valueType, NOT_MIGRATED).apply(record);
  }
}
