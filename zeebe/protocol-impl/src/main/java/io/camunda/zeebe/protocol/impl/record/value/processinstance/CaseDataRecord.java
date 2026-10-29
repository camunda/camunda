/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.CaseDataValue;
import org.agrona.DirectBuffer;

public final class CaseDataRecord extends UnifiedRecordValue implements CaseDataValue {

  private static final StringValue TITLE_KEY = new StringValue("title");

  private final StringProperty titleProp = new StringProperty(TITLE_KEY, "");

  public CaseDataRecord() {
    super(1);
    declareProperty(titleProp);
  }

  @Override
  public String getTitle() {
    return bufferAsString(titleProp.getValue());
  }

  public CaseDataRecord setTitle(final String title) {
    titleProp.setValue(title);
    return this;
  }

  public CaseDataRecord setTitle(final DirectBuffer title) {
    titleProp.setValue(title);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getTitleBuffer() {
    return titleProp.getValue();
  }

  public void wrap(final CaseDataRecord caseData) {
    titleProp.setValue(caseData.getTitleBuffer());
  }

  @Override
  public int hashCode() {
    return getTitle().hashCode();
  }

  @Override
  public boolean equals(final Object o) {
    return this == o
        || o instanceof final CaseDataRecord that && getTitle().equals(that.getTitle());
  }
}
