/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate;

import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.EnumSet;

public class OperateElasticSearchRecordFilter implements RecordFilter {

  private static final EnumSet<ValueType> ACCEPTED_VALUE_TYPES =
      EnumSet.of(
          ValueType.DECISION,
          ValueType.DECISION_REQUIREMENTS,
          ValueType.DECISION_EVALUATION,
          ValueType.INCIDENT,
          ValueType.PROCESS,
          ValueType.PROCESS_INSTANCE,
          ValueType.VARIABLE,
          ValueType.VARIABLE_DOCUMENT,
          ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
          ValueType.JOB);

  @Override
  public boolean acceptType(RecordType recordType) {
    return recordType == RecordType.EVENT;
  }

  @Override
  public boolean acceptValue(ValueType valueType) {
    // TODO Auto-generated method stub
    return false;
  }
}
