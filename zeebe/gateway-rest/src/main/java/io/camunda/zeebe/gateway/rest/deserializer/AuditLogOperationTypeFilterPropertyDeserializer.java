/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.AdvancedOperationTypeFilter;
import io.camunda.zeebe.gateway.protocol.rest.AuditLogOperationTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.OperationTypeFilterProperty;

public class AuditLogOperationTypeFilterPropertyDeserializer
    extends FilterDeserializer<OperationTypeFilterProperty, AuditLogOperationTypeEnum> {

  @Override
  protected Class<? extends OperationTypeFilterProperty> getFinalType() {
    return AdvancedOperationTypeFilter.class;
  }

  @Override
  protected Class<AuditLogOperationTypeEnum> getImplicitValueType() {
    return AuditLogOperationTypeEnum.class;
  }

  @Override
  protected OperationTypeFilterProperty createFromImplicitValue(
      final AuditLogOperationTypeEnum value) {
    final var filter = new AdvancedOperationTypeFilter();
    filter.set$Eq(value);
    return filter;
  }
}
