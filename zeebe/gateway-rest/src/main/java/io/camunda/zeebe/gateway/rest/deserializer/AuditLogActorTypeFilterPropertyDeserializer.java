/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedActorTypeFilter;
import io.camunda.gateway.protocol.model.AuditLogActorTypeEnum;
import io.camunda.gateway.protocol.model.AuditLogActorTypeFilterProperty;

public class AuditLogActorTypeFilterPropertyDeserializer
    extends FilterDeserializer<AuditLogActorTypeFilterProperty, AuditLogActorTypeEnum> {

  @Override
  protected Class<? extends AuditLogActorTypeFilterProperty> getFinalType() {
    return AdvancedActorTypeFilter.class;
  }

  @Override
  protected Class<AuditLogActorTypeEnum> getImplicitValueType() {
    return AuditLogActorTypeEnum.class;
  }

  @Override
  protected AuditLogActorTypeFilterProperty createFromImplicitValue(
      final AuditLogActorTypeEnum value) {
    final var filter = new AdvancedActorTypeFilter();
    filter.set$Eq(value);
    return filter;
  }
}
