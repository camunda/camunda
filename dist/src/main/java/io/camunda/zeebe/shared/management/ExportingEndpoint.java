/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.gateway.admin.exporting.ExportingControlApi;
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Component
@RestControllerEndpoint(id = "exporting")
public final class ExportingEndpoint {
  static final String PAUSE = "pause";
  static final String RESUME = "resume";
  final ExportingControlApi exportingService;

  @Autowired
  public ExportingEndpoint(final ExportingControlApi exportingService) {
    this.exportingService = exportingService;
  }

  @PostMapping(path = "/{operationKey}")
  public WebEndpointResponse<?> post(
      @PathVariable("operationKey") final String operationKey,
      @RequestParam(defaultValue = "false") final boolean soft) {

    try {
      final boolean softPause = soft;
      final var result =
          switch (operationKey) {
            case RESUME -> exportingService.resumeExporting();
            case PAUSE ->
                softPause
                    ? exportingService.softPauseExporting()
                    : exportingService.pauseExporting();
            default -> throw new UnsupportedOperationException();
          };
      result.join();
      return new WebEndpointResponse<>(WebEndpointResponse.STATUS_NO_CONTENT);
    } catch (final CompletionException e) {
      return new WebEndpointResponse<>(
          e.getCause(), WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    } catch (final Exception e) {
      return new WebEndpointResponse<>(e, WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    }
  }
}
