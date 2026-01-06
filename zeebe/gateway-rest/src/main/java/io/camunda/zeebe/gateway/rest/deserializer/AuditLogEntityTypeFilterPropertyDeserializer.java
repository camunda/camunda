/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.AdvancedEntityTypeFilter;
import io.camunda.zeebe.gateway.protocol.rest.AuditLogEntityTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.EntityTypeFilterProperty;

public class AuditLogEntityTypeFilterPropertyDeserializer
    extends FilterDeserializer<EntityTypeFilterProperty, AuditLogEntityTypeEnum> {

  @Override
  protected Class<? extends EntityTypeFilterProperty> getFinalType() {
    return AdvancedEntityTypeFilter.class;
  }

  @Override
  protected Class<AuditLogEntityTypeEnum> getImplicitValueType() {
    return AuditLogEntityTypeEnum.class;
  }

  @Override
  protected EntityTypeFilterProperty createFromImplicitValue(final AuditLogEntityTypeEnum value) {
    final var filter = new AdvancedEntityTypeFilter();
    filter.set$Eq(value);
    return filter;
  }
}
