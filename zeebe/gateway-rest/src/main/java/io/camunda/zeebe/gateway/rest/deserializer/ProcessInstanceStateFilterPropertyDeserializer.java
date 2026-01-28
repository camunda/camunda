/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedProcessInstanceStateFilter;
import io.camunda.gateway.protocol.model.ProcessInstanceStateEnum;
import io.camunda.gateway.protocol.model.ProcessInstanceStateFilterProperty;

public class ProcessInstanceStateFilterPropertyDeserializer
    extends FilterDeserializer<ProcessInstanceStateFilterProperty, ProcessInstanceStateEnum> {

  @Override
  protected Class<? extends ProcessInstanceStateFilterProperty> getFinalType() {
    return AdvancedProcessInstanceStateFilter.class;
  }

  @Override
  protected Class<ProcessInstanceStateEnum> getImplicitValueType() {
    return ProcessInstanceStateEnum.class;
  }

  @Override
  protected ProcessInstanceStateFilterProperty createFromImplicitValue(
      final ProcessInstanceStateEnum value) {
    final var filter = new AdvancedProcessInstanceStateFilter();
    filter.set$Eq(value);
    return filter;
  }
}
