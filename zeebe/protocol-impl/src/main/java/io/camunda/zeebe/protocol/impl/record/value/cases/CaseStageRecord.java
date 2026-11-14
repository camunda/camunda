/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.cases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue.CaseStageValue;
import io.camunda.zeebe.protocol.record.value.CaseStageStatus;
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

  private final StringProperty elementIdProp = new StringProperty("elementId");
  private final EnumProperty<CaseStageStatus> statusProp =
      new EnumProperty<>("status", CaseStageStatus.class, CaseStageStatus.NOT_STARTED);

  public CaseStageRecord() {
    super(2);
    declareProperty(elementIdProp).declareProperty(statusProp);
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

  public void copy(final CaseStageValue object) {
    setElementId(object.getElementId());
    setStatus(object.getStatus());
  }
}
