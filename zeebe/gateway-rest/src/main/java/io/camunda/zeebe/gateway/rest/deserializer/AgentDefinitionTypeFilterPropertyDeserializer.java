/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedAgentDefinitionTypeFilter;
import io.camunda.gateway.protocol.model.AgentDefinitionTypeEnum;
import io.camunda.gateway.protocol.model.AgentDefinitionTypeFilterProperty;

public class AgentDefinitionTypeFilterPropertyDeserializer
    extends FilterDeserializer<AgentDefinitionTypeFilterProperty, AgentDefinitionTypeEnum> {

  @Override
  protected Class<? extends AgentDefinitionTypeFilterProperty> getFinalType() {
    return AdvancedAgentDefinitionTypeFilter.class;
  }

  @Override
  protected Class<AgentDefinitionTypeEnum> getImplicitValueType() {
    return AgentDefinitionTypeEnum.class;
  }

  @Override
  protected AgentDefinitionTypeFilterProperty createFromImplicitValue(
      final AgentDefinitionTypeEnum value) {
    return AdvancedAgentDefinitionTypeFilter.Builder.create().$eq(value).build();
  }
}
