/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;

import javax.ws.rs.core.Response;
import java.util.function.Supplier;

@AllArgsConstructor
public class HealthClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public Response getReadiness() {
    return getRequestExecutor()
      .withoutAuthentication()
      .buildGetReadinessRequest()
      .execute();
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
