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
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue.CaseStageValue;
import io.camunda.zeebe.protocol.record.value.CaseStageStatus;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
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
  private final EnumProperty<CaseStageStatus> statusProp =
      new EnumProperty<>("status", CaseStageStatus.class, CaseStageStatus.NOT_STARTED);
  private final StringProperty nameProp = new StringProperty("name", "");
  private final BooleanProperty alternativeProp = new BooleanProperty("alternative", false);
  private final ArrayProperty<StringValue> nextElementIdsProp =
      new ArrayProperty<>("nextElementIds", StringValue::new);

  public CaseStageRecord() {
    super(5);
    declareProperty(elementIdProp)
        .declareProperty(statusProp)
        .declareProperty(nameProp)
        .declareProperty(alternativeProp)
        .declareProperty(nextElementIdsProp);
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
  public CaseStageStatus getStatus() {
    return Objects.requireNonNull(statusProp.getValue());
  }

  public CaseStageRecord setStatus(final CaseStageStatus status) {
    statusProp.setValue(status);
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
  public boolean isAlternative() {
    return alternativeProp.getValue();
  }

  public CaseStageRecord setAlternative(final boolean alternative) {
    alternativeProp.setValue(alternative);
    return this;
  }

  @Override
  public List<String> getNextElementIds() {
    return nextElementIdsProp.stream()
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .toList();
  }

  public CaseStageRecord addNextElementId(final String elementId) {
    nextElementIdsProp.add().wrap(BufferUtil.wrapString(elementId));
    return this;
  }
}
