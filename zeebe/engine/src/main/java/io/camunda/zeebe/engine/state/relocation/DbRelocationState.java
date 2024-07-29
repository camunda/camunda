/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.relocation;

import io.camunda.zeebe.engine.state.mutable.MutableRelocationState;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashSet;
import java.util.Set;
import org.agrona.DirectBuffer;

public class DbRelocationState implements MutableRelocationState {
  private RoutingInfo routingInfo;
  private final Set<String> relocatingCorrelationKeys = new HashSet<>();

  @Override
  public RoutingInfo getRoutingInfo() {
    return routingInfo;
  }

  @Override
  public void setRoutingInfo(final RoutingInfo routingInfo) {
    this.routingInfo = routingInfo;
  }

  @Override
  public void markAsRelocating(final DirectBuffer correlationKey) {
    relocatingCorrelationKeys.add(BufferUtil.bufferAsString(correlationKey));
  }
}
