/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.mapper.GlobalListenerMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.validator.GlobalListenerRequestValidator;
import io.camunda.gateway.protocol.model.CreateGlobalTaskListenerRequest;
import io.camunda.gateway.protocol.model.GlobalTaskListenerSearchQueryRequest;
import io.camunda.gateway.protocol.model.UpdateGlobalTaskListenerRequest;
import io.camunda.search.query.GlobalListenerQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/global-task-listeners")
public class GlobalTaskListenerController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final GlobalListenerMapper globalListenerMapper;

  public GlobalTaskListenerController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider,
      final IdentifierValidator identifierValidator) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
    globalListenerMapper =
        new GlobalListenerMapper(new GlobalListenerRequestValidator(identifierValidator));
  }

  @CamundaPostMapping()
  public CompletableFuture<ResponseEntity<Object>> createGlobalTaskListener(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final CreateGlobalTaskListenerRequest request) {
    return globalListenerMapper
        .toGlobalTaskListenerCreateRequest(request)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped -> createGlobalListener(physicalTenantId, mapped));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{id}")
  public ResponseEntity<Object> getGlobalTaskListener(
      @PhysicalTenantId final String physicalTenantId, @PathVariable("id") final String id) {
    return globalListenerMapper
        .toGlobalTaskListenerGetRequest(id)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped -> getGlobalListener(physicalTenantId, mapped));
  }

  @CamundaPutMapping(path = "/{id}")
  public CompletableFuture<ResponseEntity<Object>> updateGlobalTaskListener(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("id") final String id,
      @RequestBody final UpdateGlobalTaskListenerRequest request) {
    return globalListenerMapper
        .toGlobalTaskListenerUpdateRequest(id, request)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped -> updateGlobalListener(physicalTenantId, mapped));
  }

  @CamundaDeleteMapping(path = "/{id}")
  public CompletableFuture<ResponseEntity<Object>> deleteGlobalTaskListener(
      @PhysicalTenantId final String physicalTenantId, @PathVariable("id") final String id) {
    return globalListenerMapper
        .toGlobalTaskListenerDeleteRequest(id)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped -> deleteGlobalListener(physicalTenantId, mapped));
  }

  private CompletableFuture<ResponseEntity<Object>> createGlobalListener(
      final String physicalTenantId, final GlobalListenerRecord request) {
    final var globalListenerServices = serviceRegistry.globalListenerServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> globalListenerServices.createGlobalListener(request, authentication),
        globalListenerMapper::toGlobalListenerResponse,
        HttpStatus.CREATED);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  private ResponseEntity<Object> search(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final GlobalTaskListenerSearchQueryRequest request) {
    return SearchQueryRequestMapper.toGlobalTaskListenerQuery(request)
        .fold(RestErrorMapper::mapProblemToResponse, query -> search(physicalTenantId, query));
  }

  private ResponseEntity<Object> getGlobalListener(
      final String physicalTenantId, final GlobalListenerRecord request) {
    final var globalListenerServices = serviceRegistry.globalListenerServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toGlobalTaskListenerResult(
                  globalListenerServices.getGlobalTaskListener(request, authentication)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> updateGlobalListener(
      final String physicalTenantId, final GlobalListenerRecord request) {
    final var globalListenerServices = serviceRegistry.globalListenerServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> globalListenerServices.updateGlobalListener(request, authentication),
        globalListenerMapper::toGlobalListenerResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> deleteGlobalListener(
      final String physicalTenantId, final GlobalListenerRecord request) {
    final var globalListenerServices = serviceRegistry.globalListenerServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> globalListenerServices.deleteGlobalListener(request, authentication));
  }

  private ResponseEntity<Object> search(
      final String physicalTenantId, final GlobalListenerQuery query) {
    final var globalListenerServices = serviceRegistry.globalListenerServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = globalListenerServices.search(query, authentication);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toGlobalTaskListenerSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
