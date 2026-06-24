/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.types;

import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.os.transformers.OpensearchTransformer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.FieldValue;

public final class TypedValueTransformer extends OpensearchTransformer<TypedValue, FieldValue> {

  public TypedValueTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public FieldValue apply(final TypedValue value) {
    if (value.isString()) {
      return FieldValue.of(value.stringValue());
    } else if (value.isShort()) {
      return FieldValue.of(value.shortValue());
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
    throw new IllegalArgumentException("Unsupported type for TypedValue");
  }
}
