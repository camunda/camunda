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
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@WebEndpoint(id = "exporters")
@NullMarked
public class ExportersEndpoint {

  private final ClusterConfigurationManagementRequestSender requestSender;

  @Autowired
  public ExportersEndpoint(final ClusterConfigurationManagementRequestSender requestSender) {
    this.requestSender = requestSender;
  }

  /**
   * Disables an exporter. Without a {@code physicalTenant} query parameter, the exporter is
   * disabled in every physical tenant that has it configured, keeping the whole-cluster meaning the
   * operation always had. With the parameter, only the given physical tenant is affected.
   */
  @WriteOperation
  public WebEndpointResponse<?> updateExporter(
      @Selector final String exporterId,
      @Selector final String operation,
      final @Nullable String initializeFrom,
      final @Nullable Boolean dryRun,
      final @Nullable String physicalTenant) {
    final var isDryRun = Boolean.TRUE.equals(dryRun);
    return switch (operation) {
      case "disable" ->
          toWebEndpointResponse(disableExporter(exporterId, isDryRun, physicalTenant).join());
      case "enable" ->
          toWebEndpointResponse(
              enableExporter(
                      exporterId,
                      initializeFrom == null ? null : new InitializationInfo(initializeFrom),
                      isDryRun,
                      physicalTenant)
                  .join());
      default ->
          new WebEndpointResponse<>(
              "Unknown exporter operation: " + operation, WebEndpointResponse.STATUS_BAD_REQUEST);
    };
  }

  public CompletableFuture<ResponseEntity<?>> disableExporter(
      final String exporterId, final boolean dryRun, final @Nullable String physicalTenant) {

    return requestSender
        .disableExporter(new ExporterDisableRequest(exporterId, nonBlank(physicalTenant), dryRun))
        .handle(ClusterApiUtils::mapOperationResponse);
  }

  /**
   * Enables an exporter. Without a {@code physicalTenant} query parameter, the exporter is enabled
   * in every physical tenant, keeping the whole-cluster meaning the operation always had. With the
   * parameter, only the given physical tenant is affected.
   */
  public CompletableFuture<ResponseEntity<?>> enableExporter(
      final String exporterId,
      final @Nullable InitializationInfo initializeInfo,
      final boolean dryRun,
      final @Nullable String physicalTenant) {
    return requestSender
        .enableExporter(
            new ExporterEnableRequest(
                exporterId,
                Optional.ofNullable(initializeInfo).map(InitializationInfo::initializeFrom),
                nonBlank(physicalTenant),
                dryRun))
        .handle(ClusterApiUtils::mapOperationResponse);
  }

  /**
   * Deletes an exporter. Without a {@code physicalTenant} query parameter, the exporter is deleted
   * from every physical tenant that has it configured, keeping the whole-cluster meaning the
   * operation always had. With the parameter, only the given physical tenant is affected.
   */
  @DeleteOperation
  public WebEndpointResponse<?> deleteExporterOperation(
      @Selector final String exporterId,
      final @Nullable Boolean dryRun,
      final @Nullable String physicalTenant) {
    return toWebEndpointResponse(
        deleteExporter(exporterId, Boolean.TRUE.equals(dryRun), physicalTenant).join());
  }

  public CompletableFuture<ResponseEntity<?>> deleteExporter(
      final String exporterId, final boolean dryRun, final @Nullable String physicalTenant) {

    return requestSender
        .deleteExporter(new ExporterDeleteRequest(exporterId, nonBlank(physicalTenant), dryRun))
        .handle(ClusterApiUtils::mapOperationResponse);
  }

  /**
   * Lists the exporters of every physical tenant, grouped by tenant, or of the one named by the
   * {@code physicalTenant} query parameter. Each entry names the tenant it belongs to — see {@link
   * ClusterApiUtils#aggregateExporterState}.
   */
  @ReadOperation(produces = "application/json")
  public WebEndpointResponse<?> listExportersOperation(final @Nullable String physicalTenant) {
    return toWebEndpointResponse(listExporters(physicalTenant).join());
  }

  public CompletableFuture<ResponseEntity<?>> listExporters(final @Nullable String physicalTenant) {
    final var tenant = nonBlank(physicalTenant);
    final CompletableFuture<ResponseEntity<?>> topologyResponse =
        requestSender.getTopology().thenApply(response -> mapQueryResponse(tenant, response));
    return topologyResponse.exceptionally(ClusterApiUtils::mapError);
  }

  private ResponseEntity<?> mapQueryResponse(
      final Optional<String> physicalTenant,
      final Either<ErrorResponse, CurrentClusterConfiguration> response) {
    if (response.isLeft()) {
      return ClusterApiUtils.mapErrorResponse(response.getLeft());
    }

    final var configuration = response.get();
    // an uninitialized configuration has no partition groups yet (broker startup, or right after a
    // restore), so absence there means "not bootstrapped", not "unknown tenant" - reporting an
    // empty exporter list is then more truthful than a 404 on a tenant that is merely not up yet.
    if (physicalTenant.isPresent()
        && !configuration.isUninitialized()
        && !configuration.hasPartitionGroup(physicalTenant.get())) {
      return ClusterApiUtils.mapErrorResponse(
          new ErrorResponse(
              ErrorCode.NOT_FOUND,
              "Physical tenant '" + physicalTenant.get() + "' does not exist"));
    }

    return ResponseEntity.status(200)
        .body(ClusterApiUtils.aggregateExporterState(configuration, physicalTenant.orElse(null)));
  }

  private static Optional<String> nonBlank(final @Nullable String value) {
    return Optional.ofNullable(value).filter(s -> !s.isBlank());
  }

  private static WebEndpointResponse<?> toWebEndpointResponse(final ResponseEntity<?> response) {
    return new WebEndpointResponse<>(response.getBody(), response.getStatusCode().value());
  }

  private record InitializationInfo(String initializeFrom) {}
}
