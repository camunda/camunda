/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionType;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class RuntimeInstructionValue extends UnpackedObject implements DbValue {

  private final StringProperty afterElementIdProp = new StringProperty("afterElementId", "");
  private final EnumProperty<RuntimeInstructionType> typeProp =
      new EnumProperty<>(
          "type", RuntimeInstructionType.class, RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE);

  public RuntimeInstructionValue() {
    super(2);
    declareProperty(afterElementIdProp);
    declareProperty(typeProp);
  }

  public String getAfterElementId() {
    return BufferUtil.bufferAsString(afterElementIdProp.getValue());
  }

  public void setAfterElementId(final String afterElementId) {
    afterElementIdProp.setValue(afterElementId);
  }

  public RuntimeInstructionType getType() {
    return typeProp.getValue();
  }

  public void setType(final RuntimeInstructionType type) {
    typeProp.setValue(type);
  }
}
