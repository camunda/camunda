/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedClusterVariableKindFilter;
import io.camunda.gateway.protocol.model.ClusterVariableKindEnum;
import io.camunda.gateway.protocol.model.ClusterVariableKindFilterProperty;

public class ClusterVariableKindFilterPropertyDeserializer
    extends FilterDeserializer<ClusterVariableKindFilterProperty, ClusterVariableKindEnum> {

  @Override
  protected Class<? extends ClusterVariableKindFilterProperty> getFinalType() {
    return AdvancedClusterVariableKindFilter.class;
  }

  @Override
  protected Class<ClusterVariableKindEnum> getImplicitValueType() {
    return ClusterVariableKindEnum.class;
  }

  @Override
  protected ClusterVariableKindFilterProperty createFromImplicitValue(
      final ClusterVariableKindEnum value) {
    return AdvancedClusterVariableKindFilter.Builder.create().$eq(value).build();
  }
}
