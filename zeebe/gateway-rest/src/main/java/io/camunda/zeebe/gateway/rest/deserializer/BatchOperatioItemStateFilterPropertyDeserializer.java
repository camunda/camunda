/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedBatchOperationItemStateFilter;
import io.camunda.gateway.protocol.model.BatchOperationItemStateEnum;
import io.camunda.gateway.protocol.model.BatchOperationItemStateFilterProperty;

public class BatchOperatioItemStateFilterPropertyDeserializer
    extends FilterDeserializer<BatchOperationItemStateFilterProperty, BatchOperationItemStateEnum> {

  @Override
  protected Class<? extends BatchOperationItemStateFilterProperty> getFinalType() {
    return AdvancedBatchOperationItemStateFilter.class;
  }

  @Override
  protected Class<BatchOperationItemStateEnum> getImplicitValueType() {
    return BatchOperationItemStateEnum.class;
  }

  @Override
  protected BatchOperationItemStateFilterProperty createFromImplicitValue(
      final BatchOperationItemStateEnum value) {
    final var filter = new AdvancedBatchOperationItemStateFilter();
    filter.set$Eq(value);
    return filter;
  }
}
