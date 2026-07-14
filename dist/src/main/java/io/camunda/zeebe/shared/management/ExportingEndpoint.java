/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.service.ExportingServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.util.VisibleForTesting;
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
// Only load where the ServiceRegistry bean it depends on exists (i.e. when an HTTP gateway is
// enabled); otherwise the endpoint fails to construct and breaks broker/gRPC-only startup.
@ConditionalOnAnyHttpGatewayEnabled
public final class ExportingEndpoint {
  static final String PAUSE = "pause";
  static final String RESUME = "resume";
  final ExportingServices exportingServices;

  @Autowired
  public ExportingEndpoint(final ServiceRegistry serviceRegistry) {
    // TODO: The hardcoded default tenant will be removed in
    //   https://github.com/camunda/camunda/issues/57011
    this(serviceRegistry.exportingServices(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID));
  }

  @VisibleForTesting
  public ExportingEndpoint(final ExportingServices exportingServices) {
    this.exportingServices = exportingServices;
  }

  @PostMapping(path = "/{operationKey}")
  public WebEndpointResponse<?> post(
      @PathVariable("operationKey") final String operationKey,
      @RequestParam(defaultValue = "false") final boolean soft) {

    try {
      final boolean softPause = soft;
      final var result =
          switch (operationKey) {
            case RESUME -> exportingServices.resumeExporting();
            case PAUSE ->
                softPause
                    ? exportingServices.softPauseExporting()
                    : exportingServices.pauseExporting();
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
