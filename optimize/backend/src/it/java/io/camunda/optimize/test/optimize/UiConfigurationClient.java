/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class UiConfigurationClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public UIConfigurationResponseDto getUIConfiguration() {
    return requestExecutorSupplier
        .get()
        .withoutAuthentication()
        .buildGetUIConfigurationRequest()
        .execute(UIConfigurationResponseDto.class, Response.Status.OK.getStatusCode());
  }
}
