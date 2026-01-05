/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedIncidentErrorTypeFilter;
import io.camunda.gateway.protocol.model.IncidentErrorTypeEnum;
import io.camunda.gateway.protocol.model.IncidentErrorTypeFilterProperty;

public class IncidentErrorTypePropertyDeserializer
    extends FilterDeserializer<IncidentErrorTypeFilterProperty, IncidentErrorTypeEnum> {

  @Override
  protected Class<? extends IncidentErrorTypeFilterProperty> getFinalType() {
    return AdvancedIncidentErrorTypeFilter.class;
  }

  @Override
  protected Class<IncidentErrorTypeEnum> getImplicitValueType() {
    return IncidentErrorTypeEnum.class;
  }

  @Override
  protected IncidentErrorTypeFilterProperty createFromImplicitValue(
      final IncidentErrorTypeEnum value) {
    final var filter = new AdvancedIncidentErrorTypeFilter();
    filter.set$Eq(value);
    return filter;
  }
}
