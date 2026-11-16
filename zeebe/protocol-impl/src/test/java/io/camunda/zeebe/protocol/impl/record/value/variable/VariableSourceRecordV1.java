/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import io.camunda.zeebe.protocol.record.value.VariableSourceValue;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class VariableSourceRecordV1 extends UnifiedRecordValue
    implements VariableSourceValue {

  //  public static final VariableSourceRecordV1 API =
  //      new VariableSourceRecordV1().setType(VariableOperationType.API);
  //  public static final VariableSourceRecordV1 NONE =
  //      new VariableSourceRecordV1().setType(VariableOperationType.UNKNOWN);

  private static final StringValue TYPE_KEY = new StringValue("type");

  // possible enhancements documented in https://github.com/camunda/camunda/issues/46970
  private final EnumProperty<VariableOperationType> typeProp =
      new EnumProperty<>(TYPE_KEY, VariableOperationType.class, VariableOperationType.UNKNOWN);

  public VariableSourceRecordV1() {
    super(1);
    declareProperty(typeProp);
  }

  @Override
  public VariableOperationType getType() {
    return typeProp.getValue();
  }

  public VariableSourceRecordV1 setType(final VariableOperationType variableSourceType) {
    typeProp.setValue(variableSourceType);
    return this;
  }

  public void wrap(final VariableSourceRecordV1 variableSourceRecord) {
    setType(variableSourceRecord.getType());
  }

  public static VariableSourceRecordV1 api() {
    return new VariableSourceRecordV1().setType(VariableOperationType.API);
  }

  public static VariableSourceRecordV1 userTaskCompletion() {
    return new VariableSourceRecordV1().setType(VariableOperationType.USER_TASK_COMPLETION);
  }

  public static VariableSourceRecordV1 none() {
    return new VariableSourceRecordV1().setType(VariableOperationType.UNKNOWN);
  }
}
