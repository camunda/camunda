/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.AdvancedLongFilter;
import io.camunda.zeebe.gateway.protocol.rest.LongFilterProperty;

public class LongFilterPropertyDeserializer extends FilterDeserializer<LongFilterProperty, Long> {

  @Override
  protected Class<? extends LongFilterProperty> getFinalType() {
    return AdvancedLongFilter.class;
  }

  @Override
  protected Class<Long> getImplicitValueType() {
    return Long.class;
  }

  @Override
  protected LongFilterProperty createFromImplicitValue(final Long value) {
    final var filter = new AdvancedLongFilter();
    filter.set$Eq(value);
    return filter;
  }
}
