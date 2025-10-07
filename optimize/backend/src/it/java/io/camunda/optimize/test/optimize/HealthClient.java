/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import io.camunda.optimize.OptimizeRequestExecutor;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class HealthClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public Response getReadiness() {
    log.info("Sending readiness check request to /readyz endpoint...");
    try {
      Response response =
          getRequestExecutor().withoutAuthentication().buildGetReadinessRequest().execute();
      int status = response.getStatus();
      String body = "";
      if (response.hasEntity()) {
        body = response.readEntity(String.class);
      }
      log.info("Received readiness response: status={}, body={}", status, body);
      if (status != 200) {
        log.warn("Readiness check failed with status {} and body: {}", status, body);
      }
      return response;
    } catch (Exception e) {
      log.error("Exception during readiness check: {}", e.getMessage(), e);
      throw e;
    }
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
