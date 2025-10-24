/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDeleteRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDisableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterEnableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Component
@RestControllerEndpoint(id = "exporters")
public class ExportersEndpoint {

  private final ClusterConfigurationManagementRequestSender requestSender;

  @Autowired
  public ExportersEndpoint(final ClusterConfigurationManagementRequestSender requestSender) {
    this.requestSender = requestSender;
  }

  @PostMapping(path = "/{exporterId}/disable")
  public CompletableFuture<ResponseEntity<?>> disableExporter(
      @PathVariable("exporterId") final String exporterId,
      @RequestParam(defaultValue = "false") final boolean dryRun) {

    return requestSender
        .disableExporter(new ExporterDisableRequest(exporterId, dryRun))
        .handle(ClusterApiUtils::mapOperationResponse);
  }

  @PostMapping(path = "/{exporterId}/enable")
  public CompletableFuture<ResponseEntity<?>> enableExporter(
      @PathVariable("exporterId") final String exporterId,
      @RequestBody(required = false) final InitializationInfo initializeInfo,
      @RequestParam(defaultValue = "false") final boolean dryRun) {
    return requestSender
        .enableExporter(
            new ExporterEnableRequest(
                exporterId,
                Optional.ofNullable(initializeInfo).map(InitializationInfo::initializeFrom),
                dryRun))
        .handle(ClusterApiUtils::mapOperationResponse);
  }

  @DeleteMapping(path = "/{exporterId}")
  public CompletableFuture<ResponseEntity<?>> deleteExporter(
      @PathVariable("exporterId") final String exporterId,
      @RequestParam(defaultValue = "false") final boolean dryRun) {

    return requestSender
        .deleteExporter(new ExporterDeleteRequest(exporterId, dryRun))
        .handle(ClusterApiUtils::mapOperationResponse);
  }

  @GetMapping(produces = "application/json")
  public CompletableFuture<ResponseEntity<?>> listExporters() {
    return requestSender.getTopology().handle(this::mapQueryResponse);
  }

  private ResponseEntity<?> mapQueryResponse(
      final Either<ErrorResponse, ClusterConfiguration> response, final Throwable throwable) {
    if (throwable != null) {
      return ClusterApiUtils.mapError(throwable);
    }

    if (response.isLeft()) {
      return ClusterApiUtils.mapErrorResponse(response.getLeft());
    }

    return ResponseEntity.status(200).body(ClusterApiUtils.aggregateExporterState(response.get()));
  }

  private record InitializationInfo(String initializeFrom) {}
}
