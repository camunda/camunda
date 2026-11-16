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
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import java.util.Objects;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class AwaitProcessInstanceResultMetadataV1 extends UnpackedObject implements DbValue {

  private final LongProperty requestIdProperty = new LongProperty("requestId", -1);
  private final IntegerProperty requestStreamIdProperty =
      new IntegerProperty("requestStreamId", -1);
  private final ArrayProperty<StringValue> fetchVariablesProperty =
      new ArrayProperty<>("fetchVariables", StringValue::new);

  public AwaitProcessInstanceResultMetadataV1() {
    super(3);
    declareProperty(requestIdProperty)
        .declareProperty(requestStreamIdProperty)
        .declareProperty(fetchVariablesProperty);
  }

  public long getRequestId() {
    return requestIdProperty.getValue();
  }

  public AwaitProcessInstanceResultMetadataV1 setRequestId(final long requestId) {
    requestIdProperty.setValue(requestId);
    return this;
  }

  public int getRequestStreamId() {
    return requestStreamIdProperty.getValue();
  }

  public AwaitProcessInstanceResultMetadataV1 setRequestStreamId(final int requestStreamId) {
    requestStreamIdProperty.setValue(requestStreamId);
    return this;
  }

  public ArrayProperty<StringValue> fetchVariables() {
    return fetchVariablesProperty;
  }

  public AwaitProcessInstanceResultMetadataV1 setFetchVariables(
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
    final AwaitProcessInstanceResultMetadataV1 that = (AwaitProcessInstanceResultMetadataV1) o;
    return requestIdProperty.equals(that.requestIdProperty)
        && requestStreamIdProperty.equals(that.requestStreamIdProperty)
        && fetchVariablesProperty.equals(that.fetchVariablesProperty);
  }
}
