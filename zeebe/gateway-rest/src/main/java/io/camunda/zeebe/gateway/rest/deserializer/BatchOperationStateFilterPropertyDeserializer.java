/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedBatchOperationStateFilter;
import io.camunda.gateway.protocol.model.BatchOperationStateEnum;
import io.camunda.gateway.protocol.model.BatchOperationStateFilterProperty;

public class BatchOperationStateFilterPropertyDeserializer
    extends FilterDeserializer<BatchOperationStateFilterProperty, BatchOperationStateEnum> {

  @Override
  protected Class<? extends BatchOperationStateFilterProperty> getFinalType() {
    return AdvancedBatchOperationStateFilter.class;
  }

  @Override
  protected Class<BatchOperationStateEnum> getImplicitValueType() {
    return BatchOperationStateEnum.class;
  }

  @Override
  protected BatchOperationStateFilterProperty createFromImplicitValue(
      final BatchOperationStateEnum value) {
    final var filter = new AdvancedBatchOperationStateFilter();
    filter.set$Eq(value);
    return filter;
  }
}
