/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedAgentInstanceHistoryCommitStatusFilter;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryCommitStatusEnum;
import io.camunda.gateway.protocol.model.AgentInstanceHistoryCommitStatusFilterProperty;

public class AgentInstanceHistoryCommitStatusFilterPropertyDeserializer
    extends FilterDeserializer<
        AgentInstanceHistoryCommitStatusFilterProperty, AgentInstanceHistoryCommitStatusEnum> {

  @Override
  protected Class<? extends AgentInstanceHistoryCommitStatusFilterProperty> getFinalType() {
    return AdvancedAgentInstanceHistoryCommitStatusFilter.class;
  }

  @Override
  protected Class<AgentInstanceHistoryCommitStatusEnum> getImplicitValueType() {
    return AgentInstanceHistoryCommitStatusEnum.class;
  }

  @Override
  protected AgentInstanceHistoryCommitStatusFilterProperty createFromImplicitValue(
      final AgentInstanceHistoryCommitStatusEnum value) {
    return AdvancedAgentInstanceHistoryCommitStatusFilter.Builder.create().$eq(value).build();
  }
}
