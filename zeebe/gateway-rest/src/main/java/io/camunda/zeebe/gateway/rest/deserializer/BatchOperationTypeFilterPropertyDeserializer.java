/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedBatchOperationTypeFilter;
import io.camunda.gateway.protocol.model.BatchOperationTypeEnum;
import io.camunda.gateway.protocol.model.BatchOperationTypeFilterProperty;

public class BatchOperationTypeFilterPropertyDeserializer
    extends FilterDeserializer<BatchOperationTypeFilterProperty, BatchOperationTypeEnum> {

  @Override
  protected Class<? extends BatchOperationTypeFilterProperty> getFinalType() {
    return AdvancedBatchOperationTypeFilter.class;
  }

  @Override
  protected Class<BatchOperationTypeEnum> getImplicitValueType() {
    return BatchOperationTypeEnum.class;
  }

  @Override
  protected BatchOperationTypeFilterProperty createFromImplicitValue(
      final BatchOperationTypeEnum value) {
    final var filter = new AdvancedBatchOperationTypeFilter();
    filter.set$Eq(value);
    return filter;
  }
}
