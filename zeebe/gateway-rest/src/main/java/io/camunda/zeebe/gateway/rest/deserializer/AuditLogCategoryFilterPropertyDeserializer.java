/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.AdvancedCategoryFilter;
import io.camunda.zeebe.gateway.protocol.rest.AuditLogCategoryEnum;
import io.camunda.zeebe.gateway.protocol.rest.CategoryFilterProperty;

public class AuditLogCategoryFilterPropertyDeserializer
    extends FilterDeserializer<CategoryFilterProperty, AuditLogCategoryEnum> {

  @Override
  protected Class<? extends CategoryFilterProperty> getFinalType() {
    return AdvancedCategoryFilter.class;
  }

  @Override
  protected Class<AuditLogCategoryEnum> getImplicitValueType() {
    return AuditLogCategoryEnum.class;
  }

  @Override
  protected CategoryFilterProperty createFromImplicitValue(final AuditLogCategoryEnum value) {
    final var filter = new AdvancedCategoryFilter();
    filter.set$Eq(value);
    return filter;
  }
}
