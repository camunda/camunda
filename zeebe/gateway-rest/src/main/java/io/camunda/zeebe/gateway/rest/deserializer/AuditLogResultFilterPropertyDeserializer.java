/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedResultFilter;
import io.camunda.gateway.protocol.model.AuditLogResultEnum;
import io.camunda.gateway.protocol.model.AuditLogResultFilterProperty;

public class AuditLogResultFilterPropertyDeserializer
    extends FilterDeserializer<AuditLogResultFilterProperty, AuditLogResultEnum> {

  @Override
  protected Class<? extends AuditLogResultFilterProperty> getFinalType() {
    return AdvancedResultFilter.class;
  }

  @Override
  protected Class<AuditLogResultEnum> getImplicitValueType() {
    return AuditLogResultEnum.class;
  }

  @Override
  protected AuditLogResultFilterProperty createFromImplicitValue(final AuditLogResultEnum value) {
    final var filter = new AdvancedResultFilter();
    filter.set$Eq(value);
    return filter;
  }
}
