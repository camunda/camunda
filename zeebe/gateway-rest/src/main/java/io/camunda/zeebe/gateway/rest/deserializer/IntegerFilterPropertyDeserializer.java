/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.AdvancedIntegerFilter;
import io.camunda.zeebe.gateway.protocol.rest.IntegerFilterProperty;

public class IntegerFilterPropertyDeserializer
    extends FilterDeserializer<IntegerFilterProperty, Integer> {

  @Override
  protected Class<? extends IntegerFilterProperty> getFinalType() {
    return AdvancedIntegerFilter.class;
  }

  @Override
  protected Class<Integer> getImplicitValueType() {
    return Integer.class;
  }

  @Override
  protected IntegerFilterProperty createFromImplicitValue(final Integer value) {
    final var filter = new AdvancedIntegerFilter();
    filter.set$Eq(value);
    return filter;
  }
}
