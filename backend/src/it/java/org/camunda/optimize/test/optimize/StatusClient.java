/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;

import javax.ws.rs.core.Response;
import java.util.function.Supplier;

@AllArgsConstructor
public class StatusClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public StatusWithProgressDto getImportStatus() {
    return getRequestExecutor()
      .withoutAuthentication()
      .buildCheckImportStatusRequest()
      .execute(StatusWithProgressDto.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
