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
import io.zeebe.protocol.record.value.BpmnElementType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class MigratedStreamProcessors {

  private static final List<BpmnElementType> MIGRATED_BPMN_PROCESSORS = new ArrayList<>();

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
    MIGRATED_BPMN_PROCESSORS.add(BpmnElementType.TESTING_ONLY);

    MIGRATED_VALUE_TYPES.put(ValueType.ERROR, MIGRATED);
    MIGRATED_VALUE_TYPES.put(ValueType.WORKFLOW, MIGRATED);
  }

  private MigratedStreamProcessors() {}

  public static boolean isMigrated(final TypedRecord<?> record) {
    final var valueType = record.getValueType();
    return MIGRATED_VALUE_TYPES.getOrDefault(valueType, NOT_MIGRATED).apply(record);
  }

  public static boolean isMigrated(final ValueType valueType) {
    return MIGRATED_VALUE_TYPES.get(valueType) == MIGRATED;
  }

  public static boolean isMigrated(final BpmnElementType bpmnElementType) {
    return MIGRATED_BPMN_PROCESSORS.contains(bpmnElementType);
  }
}
