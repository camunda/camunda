/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.record;

import java.util.Map;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.exporter.record.RecordValue;
import io.zeebe.exporter.record.RecordValueWithPayload;

public abstract class RecordValueWithPayloadImpl implements RecordValue, RecordValueWithPayload {
  private String payload;

  public RecordValueWithPayloadImpl() {
  }

  @Override
  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  @Override
  @JsonIgnore
  public Map<String, Object> getPayloadAsMap() {
    throw new UnsupportedOperationException("getPayloadAsMap operation is not supported");
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RecordValueWithPayloadImpl that = (RecordValueWithPayloadImpl) o;
    return Objects.equals(payload, that.payload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(payload);
  }
}
