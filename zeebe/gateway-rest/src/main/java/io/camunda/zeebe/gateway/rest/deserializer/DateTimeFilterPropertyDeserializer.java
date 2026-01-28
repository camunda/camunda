/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedDateTimeFilter;
import io.camunda.gateway.protocol.model.DateTimeFilterProperty;

public class DateTimeFilterPropertyDeserializer
    extends FilterDeserializer<DateTimeFilterProperty, String> {

  @Override
  protected Class<? extends DateTimeFilterProperty> getFinalType() {
    return AdvancedDateTimeFilter.class;
  }

  @Override
  protected Class<String> getImplicitValueType() {
    return String.class;
  }

  @Override
  protected DateTimeFilterProperty createFromImplicitValue(final String value) {
    final var filter = new AdvancedDateTimeFilter();
    filter.set$Eq(value);
    return filter;
  }
}
