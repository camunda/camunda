/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.optimize.OptimizeRequestExecutor;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;

public class LocalizationClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public LocalizationClient(final Supplier<OptimizeRequestExecutor> requestExecutorSupplier) {
    this.requestExecutorSupplier = requestExecutorSupplier;
  }

  public JsonNode getLocalizationJson(final String localeCode) {
    return getRequestExecutor()
        .buildGetLocalizationRequest(localeCode)
        .execute(JsonNode.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
