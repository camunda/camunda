/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.property;

import io.camunda.zeebe.msgpack.value.DeltaEncodedLongArrayValue;
import java.util.Objects;

public final class DeltaEncodedLongArrayProperty extends BaseProperty<DeltaEncodedLongArrayValue> {
  public DeltaEncodedLongArrayProperty(final String keyString) {
    super(keyString, new DeltaEncodedLongArrayValue());
  }

  public long[] getValues() {
    return resolveValue().getValues();
  }

  public void setValues(final long[] values) {
    resolveValue().setValues(Objects.requireNonNull(values));
    isSet = true;
  }
}
