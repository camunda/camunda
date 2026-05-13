/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.mapping.http.validator.AgentInstanceRequestValidator;
import io.camunda.gateway.protocol.model.simple.AgentInstanceCreationRequest;
import io.camunda.gateway.protocol.model.simple.AgentInstanceCreationResult;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.util.Either;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ProblemDetail;

@NullMarked
public class AgentInstanceMapper {

  private final AgentInstanceRequestValidator requestValidator;

  public AgentInstanceMapper(final AgentInstanceRequestValidator requestValidator) {
    this.requestValidator = requestValidator;
  }

  public Either<ProblemDetail, AgentInstanceRecord> toCreateAgentInstanceRecord(
      final AgentInstanceCreationRequest request) {
    return RequestMapper.getResult(
        requestValidator.validateCreateRequest(request),
        () -> {
          final var record = new AgentInstanceRecord();

          record.setElementInstanceKey(Long.parseLong(request.getElementInstanceKey()));

          final var def = request.getDefinition();
          record
              .getDefinition()
              .setModel(def.getModel())
              .setProvider(def.getProvider())
              .setSystemPrompt(def.getSystemPrompt());

          if (request.getLimits() != null) {
            final var limits = request.getLimits();
            record
                .getLimits()
                .setMaxTokens(limits.getMaxTokens())
                .setMaxModelCalls(limits.getMaxModelCalls())
                .setMaxToolCalls(limits.getMaxToolCalls());
          }

          return record;
        });
  }

  public AgentInstanceCreationResult toAgentInstanceCreationResult(
      final AgentInstanceRecord record) {
    return AgentInstanceCreationResult.Builder.create()
        .agentInstanceKey(KeyUtil.keyToString(record.getAgentInstanceKey()))
        .build();
  }
}
