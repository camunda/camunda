/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
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
  private final ClusterConfigExportingControlService exportingControlService;

  @Autowired
  public ExportingEndpoint(final ClusterConfigurationManagementRequestSender requestSender) {
    this(new ClusterConfigExportingControlService(requestSender));
  }

  ExportingEndpoint(final ClusterConfigExportingControlService exportingControlService) {
    this.exportingControlService = exportingControlService;
  }

  @PostMapping(path = "/{operationKey}")
  public WebEndpointResponse<?> post(
      @PathVariable("operationKey") final String operationKey,
      @RequestParam(defaultValue = "false") final boolean soft) {

    try {
      final var result =
          switch (operationKey) {
            case RESUME -> exportingControlService.resumeExporting(DEFAULT_PHYSICAL_TENANT_ID);
            case PAUSE ->
                soft
                    ? exportingControlService.softPauseExporting(DEFAULT_PHYSICAL_TENANT_ID)
                    : exportingControlService.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID);
            default -> throw new UnsupportedOperationException();
          };
      result.join();
      return new WebEndpointResponse<>(WebEndpointResponse.STATUS_NO_CONTENT);
    } catch (final CompletionException e) {
      final var message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      return new WebEndpointResponse<>(message, WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    } catch (final Exception e) {
      return new WebEndpointResponse<>(
          e.getMessage(), WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    }
  }
}
