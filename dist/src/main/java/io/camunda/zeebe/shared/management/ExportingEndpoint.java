/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeAwaiter;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExportingStateChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import java.time.Duration;
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
  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);
  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  private final ClusterConfigurationManagementRequestSender requestSender;
  private final ClusterConfigurationChangeAwaiter changeAwaiter;

  @Autowired
  public ExportingEndpoint(final ClusterConfigurationManagementRequestSender requestSender) {
    this(
        requestSender,
        new ClusterConfigurationChangeAwaiter(requestSender, POLL_INTERVAL, TIMEOUT));
  }

  ExportingEndpoint(
      final ClusterConfigurationManagementRequestSender requestSender,
      final ClusterConfigurationChangeAwaiter changeAwaiter) {
    this.requestSender = requestSender;
    this.changeAwaiter = changeAwaiter;
  }

  @PostMapping(path = "/{operationKey}")
  public WebEndpointResponse<?> post(
      @PathVariable("operationKey") final String operationKey,
      @RequestParam(defaultValue = "false") final boolean soft) {

    try {
      final var targetState =
          switch (operationKey) {
            case RESUME -> ExportingState.EXPORTING;
            case PAUSE -> soft ? ExportingState.SOFT_PAUSED : ExportingState.PAUSED;
            default -> throw new UnsupportedOperationException();
          };
      changeAwaiter
          .awaitCompletion(
              requestSender.changeExporterState(
                  new ExportingStateChangeRequest(targetState, false)))
          .join();
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
