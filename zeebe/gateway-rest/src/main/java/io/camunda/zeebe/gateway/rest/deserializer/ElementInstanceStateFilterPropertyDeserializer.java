/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.AdvancedElementInstanceStateFilter;
import io.camunda.zeebe.gateway.protocol.rest.ElementInstanceStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.ElementInstanceStateFilterProperty;

public class ElementInstanceStateFilterPropertyDeserializer
    extends FilterDeserializer<ElementInstanceStateFilterProperty, ElementInstanceStateEnum> {

  @Override
  protected Class<? extends ElementInstanceStateFilterProperty> getFinalType() {
    return AdvancedElementInstanceStateFilter.class;
  }

  @Override
  protected Class<ElementInstanceStateEnum> getImplicitValueType() {
    return ElementInstanceStateEnum.class;
  }

  @Override
  protected ElementInstanceStateFilterProperty createFromImplicitValue(
      final ElementInstanceStateEnum value) {
    final var filter = new AdvancedElementInstanceStateFilter();
    filter.set$Eq(value);
    return filter;
  }
}
