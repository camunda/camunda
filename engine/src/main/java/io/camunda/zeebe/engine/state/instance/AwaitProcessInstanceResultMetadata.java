/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.instance;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.value.StringValue;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import java.util.Objects;

public final class AwaitProcessInstanceResultMetadata extends UnifiedRecordValue
    implements DbValue {

  private final LongProperty requestIdProperty = new LongProperty("requestId", -1);
  private final IntegerProperty requestStreamIdProperty =
      new IntegerProperty("requestStreamId", -1);
  private final ArrayProperty<StringValue> fetchVariablesProperty =
      new ArrayProperty<>("fetchVariables", new StringValue());

  public AwaitProcessInstanceResultMetadata() {
    declareProperty(requestIdProperty)
        .declareProperty(requestStreamIdProperty)
        .declareProperty(fetchVariablesProperty);
  }

  public long getRequestId() {
    return requestIdProperty.getValue();
  }

  public AwaitProcessInstanceResultMetadata setRequestId(final long requestId) {
    requestIdProperty.setValue(requestId);
    return this;
  }

  public int getRequestStreamId() {
    return requestStreamIdProperty.getValue();
  }

  public AwaitProcessInstanceResultMetadata setRequestStreamId(final int requestStreamId) {
    requestStreamIdProperty.setValue(requestStreamId);
    return this;
  }

  public ArrayProperty<StringValue> fetchVariables() {
    return fetchVariablesProperty;
  }

  public AwaitProcessInstanceResultMetadata setFetchVariables(
      final ArrayProperty<StringValue> variables) {
    fetchVariablesProperty.reset();
    variables.forEach(variable -> fetchVariablesProperty.add().wrap(variable));
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), requestIdProperty, requestStreamIdProperty, fetchVariablesProperty);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final AwaitProcessInstanceResultMetadata that = (AwaitProcessInstanceResultMetadata) o;
    return requestIdProperty.equals(that.requestIdProperty)
        && requestStreamIdProperty.equals(that.requestStreamIdProperty)
        && fetchVariablesProperty.equals(that.fetchVariablesProperty);
  }
}
