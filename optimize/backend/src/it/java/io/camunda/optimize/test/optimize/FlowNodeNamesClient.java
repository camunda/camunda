/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import io.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;

public class FlowNodeNamesClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public FlowNodeNamesClient(final Supplier<OptimizeRequestExecutor> requestExecutorSupplier) {
    this.requestExecutorSupplier = requestExecutorSupplier;
  }

  public FlowNodeNamesResponseDto getFlowNodeNames(final FlowNodeIdsToNamesRequestDto requestDto) {
    return getRequestExecutor()
        .buildGetFlowNodeNames(requestDto)
        .execute(FlowNodeNamesResponseDto.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
