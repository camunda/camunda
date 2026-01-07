/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.BasicStringFilter;
import io.camunda.gateway.protocol.model.BasicStringFilterProperty;

public class BasicStringFilterPropertyDeserializer
    extends FilterDeserializer<BasicStringFilterProperty, String> {

  @Override
  protected Class<? extends BasicStringFilterProperty> getFinalType() {
    return BasicStringFilter.class;
  }

  @Override
  protected Class<String> getImplicitValueType() {
    return String.class;
  }

  @Override
  protected BasicStringFilterProperty createFromImplicitValue(final String value) {
    final var filter = new BasicStringFilter();
    filter.set$Eq(value);
    return filter;
  }
}
