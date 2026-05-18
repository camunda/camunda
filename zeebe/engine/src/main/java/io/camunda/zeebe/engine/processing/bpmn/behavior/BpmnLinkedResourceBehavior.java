/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import com.google.common.base.Strings;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior.JobProperties;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.state.immutable.ResourceState;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class BpmnLinkedResourceBehavior {

  private final ResourceState resourceState;
  private final BpmnStateBehavior stateBehavior;

  public BpmnLinkedResourceBehavior(
      final ResourceState resourceState, final BpmnStateBehavior stateBehavior) {
    this.resourceState = resourceState;
    this.stateBehavior = stateBehavior;
  }

  public Either<Failure, Void> createVariables(
      final BpmnElementContext context, final JobProperties j) {
    if (j.getLinkedResources() == null) {
      return Either.right(null);
    }

    j.getLinkedResources().stream()
        .filter(resource -> !Strings.isNullOrEmpty(resource.getVariableName()))
        .flatMap(
            resource ->
                resourceState
                    .findResourceByKey(
                        Long.parseLong(resource.getResourceKey()), context.getTenantId())
                    .map(
                        persistedResource ->
                            new VariableToCreate(
                                resource.getVariableName(),
                                BufferUtil.wrapArray(
                                    MsgPackConverter.convertToMsgPack(
                                        persistedResource.getResource()))))
                    .stream())
        .forEach(
            variableToCreate ->
                stateBehavior.setLocalVariable(
                    context,
                    BufferUtil.wrapString(variableToCreate.variableName()),
                    variableToCreate.resource()));

    return Either.right(null);
  }

  private record VariableToCreate(String variableName, DirectBuffer resource) {}
}
