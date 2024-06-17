/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.query.status.StatusResponseDto;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class StatusClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public StatusResponseDto getStatus() {
    return getRequestExecutor()
        .withoutAuthentication()
        .buildCheckImportStatusRequest()
        .execute(StatusResponseDto.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
