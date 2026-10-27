/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate.transformers;

import static io.camunda.zeebe.exporter.common.waitstate.WaitStateConfigs.USER_TASK_CONFIG;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformer;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformerConfig;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;

public class UserTaskBasedWaitStateTransformer
    implements WaitStateTransformer<UserTaskRecordValue> {

  @Override
  public WaitStateTransformerConfig config() {
    return USER_TASK_CONFIG;
  }

  @Override
  public void extract(final Record<UserTaskRecordValue> record, final WaitStateEntry entry) {
    final UserTaskRecordValue value = record.getValue();
    final String rawDueDate = value.getDueDate();
    final String dueDate = rawDueDate == null || rawDueDate.isBlank() ? null : rawDueDate;
    entry
        .setElementType(BpmnElementType.USER_TASK)
        .setDetails(new UserTaskWaitStateDetails(value.getUserTaskKey(), dueDate));
  }
}
