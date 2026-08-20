/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedAgentInstanceHistoryRoleFilter;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryRoleEnum;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryRoleFilterProperty;

public class AgentInstanceHistoryRoleFilterPropertyDeserializer
    extends FilterDeserializer<
        AgentInstanceHistoryRoleFilterProperty, AgentInstanceHistoryRoleEnum> {

  @Override
  protected Class<? extends AgentInstanceHistoryRoleFilterProperty> getFinalType() {
    return AdvancedAgentInstanceHistoryRoleFilter.class;
  }

  @Override
  protected Class<AgentInstanceHistoryRoleEnum> getImplicitValueType() {
    return AgentInstanceHistoryRoleEnum.class;
  }

  @Override
  protected AgentInstanceHistoryRoleFilterProperty createFromImplicitValue(
      final AgentInstanceHistoryRoleEnum value) {
    return AdvancedAgentInstanceHistoryRoleFilter.Builder.create().$eq(value).build();
  }
}
