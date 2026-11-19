/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.validator.SecretRequestValidator;
import io.camunda.gateway.protocol.model.SecretListRequest;
import io.camunda.gateway.protocol.model.SecretResolveRequest;
import io.camunda.secretstore.SecretStatus;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/secrets")
public class SecretController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public SecretController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/resolve")
  public CompletableFuture<ResponseEntity<Object>> resolveSecrets(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final SecretResolveRequest request) {
    return SecretRequestValidator.validateSecretResolveRequest(request)
        .<CompletableFuture<ResponseEntity<Object>>>map(
            RestErrorMapper::mapProblemToCompletedResponse)
        .orElseGet(() -> resolve(physicalTenantId, request.getReferences()));
  }

  private CompletableFuture<ResponseEntity<Object>> resolve(
      final String physicalTenantId, final List<String> references) {
    final var secretServices = serviceRegistry.secretServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> secretServices.resolve(references, authentication),
        ResponseMapper::toSecretResolveResult,
        HttpStatus.OK);
  }

  @CamundaPostMapping(path = "/list")
  public CompletableFuture<ResponseEntity<Object>> listSecrets(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final @Nullable SecretListRequest request) {
    return SecretRequestValidator.validateSecretListRequest(request)
        .<CompletableFuture<ResponseEntity<Object>>>map(
            RestErrorMapper::mapProblemToCompletedResponse)
        .orElseGet(() -> list(physicalTenantId, toSecretStatus(request)));
  }

  private CompletableFuture<ResponseEntity<Object>> list(
      final String physicalTenantId, final @Nullable SecretStatus status) {
    final var secretServices = serviceRegistry.secretServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> secretServices.list(authentication, status),
        ResponseMapper::toSecretListResult,
        HttpStatus.OK);
  }

  private static @Nullable SecretStatus toSecretStatus(final @Nullable SecretListRequest request) {
    final var filter = request == null ? null : request.getFilter();
    final var status = filter == null ? null : filter.getStatus();
    if (status == null) {
      return null;
    }
    return switch (status) {
      case ACTIVE -> SecretStatus.ACTIVE;
      case DISABLED -> SecretStatus.DISABLED;
      case EXPIRED -> SecretStatus.EXPIRED;
      case NOT_YET_ACTIVE -> SecretStatus.NOT_YET_ACTIVE;
    };
  }
}
