/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.AdvancedClusterVariableScopeFilter;
import io.camunda.zeebe.gateway.protocol.rest.ClusterVariableScopeEnum;
import io.camunda.zeebe.gateway.protocol.rest.ClusterVariableScopeFilterProperty;

public class ClusterVariableScopeFilterPropertyDeserializer
    extends FilterDeserializer<ClusterVariableScopeFilterProperty, ClusterVariableScopeEnum> {

  @Override
  protected Class<? extends ClusterVariableScopeFilterProperty> getFinalType() {
    return AdvancedClusterVariableScopeFilter.class;
  }

  @Override
  protected Class<ClusterVariableScopeEnum> getImplicitValueType() {
    return ClusterVariableScopeEnum.class;
  }

  @Override
  protected ClusterVariableScopeFilterProperty createFromImplicitValue(
      final ClusterVariableScopeEnum value) {
    final var filter = new AdvancedClusterVariableScopeFilter();
    filter.set$Eq(value);
    return filter;
  }
}
