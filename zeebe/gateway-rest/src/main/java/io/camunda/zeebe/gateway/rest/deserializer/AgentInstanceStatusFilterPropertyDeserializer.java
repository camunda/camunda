/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedAgentInstanceStatusFilter;
import io.camunda.gateway.protocol.model.AgentInstanceStatusEnum;
import io.camunda.gateway.protocol.model.AgentInstanceStatusFilterProperty;

public class AgentInstanceStatusFilterPropertyDeserializer
    extends FilterDeserializer<AgentInstanceStatusFilterProperty, AgentInstanceStatusEnum> {

  @Override
  protected Class<? extends AgentInstanceStatusFilterProperty> getFinalType() {
    return AdvancedAgentInstanceStatusFilter.class;
  }

  @Override
  protected Class<AgentInstanceStatusEnum> getImplicitValueType() {
    return AgentInstanceStatusEnum.class;
  }

  @Override
  protected AgentInstanceStatusFilterProperty createFromImplicitValue(
      final AgentInstanceStatusEnum value) {
    return AdvancedAgentInstanceStatusFilter.Builder.create().$eq(value).build();
  }
}
