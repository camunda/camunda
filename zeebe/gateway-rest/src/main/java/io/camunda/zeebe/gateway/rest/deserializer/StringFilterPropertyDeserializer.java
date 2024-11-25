/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.AdvancedStringFilter;
import io.camunda.zeebe.gateway.protocol.rest.StringFilterProperty;

public class StringFilterPropertyDeserializer
    extends FilterDeserializer<StringFilterProperty, String> {

  @Override
  protected Class<? extends StringFilterProperty> getFinalType() {
    return AdvancedStringFilter.class;
  }

  @Override
  protected Class<String> getImplicitValueType() {
    return String.class;
  }

  @Override
  protected StringFilterProperty createFromImplicitValue(final String value) {
    final var filter = new AdvancedStringFilter();
    filter.set$Eq(value);
    return filter;
  }
}
