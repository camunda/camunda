/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedIncidentStateFilter;
import io.camunda.gateway.protocol.model.IncidentStateEnum;
import io.camunda.gateway.protocol.model.IncidentStateFilterProperty;

public class IncidentStatePropertyDeserializer
    extends FilterDeserializer<IncidentStateFilterProperty, IncidentStateEnum> {

  @Override
  protected Class<? extends IncidentStateFilterProperty> getFinalType() {
    return AdvancedIncidentStateFilter.class;
  }

  @Override
  protected Class<IncidentStateEnum> getImplicitValueType() {
    return IncidentStateEnum.class;
  }

  @Override
  protected IncidentStateFilterProperty createFromImplicitValue(final IncidentStateEnum value) {
    final var filter = new AdvancedIncidentStateFilter();
    filter.set$Eq(value);
    return filter;
  }
}
