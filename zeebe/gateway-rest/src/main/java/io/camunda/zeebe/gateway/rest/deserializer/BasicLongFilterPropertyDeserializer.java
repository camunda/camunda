/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.BasicLongFilter;
import io.camunda.zeebe.gateway.protocol.rest.BasicLongFilterProperty;

public class BasicLongFilterPropertyDeserializer
    extends FilterDeserializer<BasicLongFilterProperty, Long> {

  @Override
  protected Class<? extends BasicLongFilterProperty> getFinalType() {
    return BasicLongFilter.class;
  }

  @Override
  protected Class<Long> getImplicitValueType() {
    return Long.class;
  }

  @Override
  protected BasicLongFilterProperty createFromImplicitValue(final Long value) {
    final var filter = new BasicLongFilter();
    filter.set$Eq(value);
    return filter;
  }
}
