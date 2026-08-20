/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.mapper.AuthorizationMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.validator.AuthorizationRequestValidator;
import io.camunda.gateway.protocol.model.AuthorizationRequest;
import io.camunda.gateway.protocol.model.AuthorizationSearchQuery;
import io.camunda.gateway.protocol.model.AuthorizationSearchResult;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.validation.AuthorizationValidator;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.AuthorizationServices.UpdateAuthorizationRequest;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/authorizations")
public class AuthorizationController {
  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final AuthorizationMapper authorizationMapper;

  public AuthorizationController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider,
      final IdentifierValidator identifierValidator) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
    authorizationMapper =
        new AuthorizationMapper(
            new AuthorizationRequestValidator(new AuthorizationValidator(identifierValidator)));
  }

  @CamundaPostMapping()
  public CompletableFuture<ResponseEntity<Object>> createAuthorization(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final AuthorizationRequest authorizationCreateRequest) {
    return authorizationMapper
        .toCreateAuthorizationRequest(authorizationCreateRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> create(physicalTenantId, request));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{authorizationKey}")
  public ResponseEntity<Object> getAuthorization(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final long authorizationKey) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toAuthorization(
                  serviceRegistry
                      .authorizationServices(physicalTenantId)
                      .getAuthorization(authorizationKey, authentication)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @CamundaDeleteMapping(path = "/{authorizationKey}")
  public CompletableFuture<ResponseEntity<Object>> delete(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final long authorizationKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            serviceRegistry
                .authorizationServices(physicalTenantId)
                .deleteAuthorization(authorizationKey, authentication));
  }

  @CamundaPutMapping(path = "/{authorizationKey}")
  public CompletableFuture<ResponseEntity<Object>> updateAuthorization(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long authorizationKey,
      @RequestBody final AuthorizationRequest authorizationUpdateRequest) {
    return authorizationMapper
        .toUpdateAuthorizationRequest(authorizationKey, authorizationUpdateRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> update(physicalTenantId, request));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<AuthorizationSearchResult> searchAuthorizations(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final AuthorizationSearchQuery query) {
    return SearchQueryRequestMapper.toAuthorizationQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, q -> search(physicalTenantId, q));
  }

  private ResponseEntity<AuthorizationSearchResult> search(
      final String physicalTenantId, final AuthorizationQuery query) {
    final var authorizationServices = serviceRegistry.authorizationServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = authorizationServices.search(query, authentication);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toAuthorizationSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> create(
      final String physicalTenantId, final CreateAuthorizationRequest createAuthorizationRequest) {
    final var authorizationServices = serviceRegistry.authorizationServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> authorizationServices.createAuthorization(createAuthorizationRequest, authentication),
        ResponseMapper::toAuthorizationCreateResponse,
        HttpStatus.CREATED);
  }

  private CompletableFuture<ResponseEntity<Object>> update(
      final String physicalTenantId, final UpdateAuthorizationRequest authorizationRequest) {
    final var authorizationServices = serviceRegistry.authorizationServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> authorizationServices.updateAuthorization(authorizationRequest, authentication));
  }
}
