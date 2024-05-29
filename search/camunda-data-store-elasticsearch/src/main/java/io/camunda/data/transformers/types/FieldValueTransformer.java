/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.types;

import co.elastic.clients.elasticsearch._types.FieldValue;
import io.camunda.data.clients.types.DataStoreFieldValue;
import io.camunda.data.mappers.DataStoreTransformer;

public class FieldValueTransformer
    implements DataStoreTransformer<DataStoreFieldValue, FieldValue> {

  @Override
  public FieldValue apply(final DataStoreFieldValue value) {
    if (value.isString()) {
      return FieldValue.of(value.stringValue());
    } else if (value.isInteger()) {
      return FieldValue.of(value.intValue());
    } else if (value.isLong()) {
      return FieldValue.of(value.longValue());
    } else if (value.isBoolean()) {
      return FieldValue.of(value.booleanValue());
    } else if (value.isDouble()) {
      return FieldValue.of(value.doubleValue());
    } else if (value.isNull()) {
      return FieldValue.NULL;
    }
    return null;
  }
}
