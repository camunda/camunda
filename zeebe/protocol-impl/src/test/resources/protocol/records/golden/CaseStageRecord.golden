/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.cases;

import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.ELEMENT_ID_KEY;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue.CaseStageValue;
import io.camunda.zeebe.protocol.record.value.CaseStageState;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class CaseStageRecord extends ObjectValue implements CaseStageValue {

  private final StringProperty elementIdProp = new StringProperty(ELEMENT_ID_KEY);
  private final StringProperty nameProp = new StringProperty("name", "");
  private final EnumProperty<CaseStageState> stateProp =
      new EnumProperty<>("state", CaseStageState.class, CaseStageState.NOT_STARTED);

  public CaseStageRecord() {
    super(3);
    declareProperty(elementIdProp).declareProperty(nameProp).declareProperty(stateProp);
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public CaseStageRecord setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public CaseStageRecord setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  @Override
  public CaseStageState getState() {
    return Objects.requireNonNull(stateProp.getValue());
  }

  public CaseStageRecord setState(final CaseStageState state) {
    stateProp.setValue(state);
    return this;
  }
}
