/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.operation;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_MIGRATED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.Set;

public class OperationFromProcessInstanceHandler
    extends AbstractOperationHandler<ProcessInstanceRecordValue> {
  protected static final Set<Intent> ELIGIBLE_STATES = Set.of(ELEMENT_MIGRATED, ELEMENT_TERMINATED);

  public OperationFromProcessInstanceHandler(final String indexName) {
    super(indexName);
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public boolean handlesRecord(final Record<ProcessInstanceRecordValue> record) {
    return isProcessEvent(record.getValue()) && ELIGIBLE_STATES.contains(record.getIntent());
  }

  protected boolean isProcessEvent(final ProcessInstanceRecordValue recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isOfType(
      final ProcessInstanceRecordValue recordValue, final BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
  }
}
