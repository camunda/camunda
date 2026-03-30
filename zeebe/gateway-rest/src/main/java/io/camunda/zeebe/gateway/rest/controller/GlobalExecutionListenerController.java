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
import io.camunda.gateway.protocol.model.CreateGlobalExecutionListenerRequest;
import io.camunda.gateway.protocol.model.GlobalExecutionListenerSearchQueryRequest;
import io.camunda.gateway.protocol.model.UpdateGlobalExecutionListenerRequest;
import io.camunda.search.query.GlobalListenerQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.GlobalListenerServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
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
@RequestMapping("/v2/global-execution-listeners")
public class GlobalExecutionListenerController {

  private final GlobalListenerServices globalListenerServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final GlobalListenerMapper globalListenerMapper;

  public GlobalExecutionListenerController(
      final GlobalListenerServices globalListenerServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final IdentifierValidator identifierValidator) {
    this.globalListenerServices = globalListenerServices;
    this.authenticationProvider = authenticationProvider;
    globalListenerMapper =
        new GlobalListenerMapper(new GlobalListenerRequestValidator(identifierValidator));
  }

  @CamundaPostMapping()
  public CompletableFuture<ResponseEntity<Object>> createGlobalExecutionListener(
      @RequestBody final CreateGlobalExecutionListenerRequest request) {
    return globalListenerMapper
        .toGlobalExecutionListenerCreateRequest(request)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createGlobalListener);
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{id}")
  public ResponseEntity<Object> getGlobalExecutionListener(@PathVariable("id") final String id) {
    return globalListenerMapper
        .toGlobalExecutionListenerGetRequest(id)
        .fold(RestErrorMapper::mapProblemToResponse, this::getGlobalListener);
  }

  @CamundaPutMapping(path = "/{id}")
  public CompletableFuture<ResponseEntity<Object>> updateGlobalExecutionListener(
      @PathVariable("id") final String id,
      @RequestBody final UpdateGlobalExecutionListenerRequest request) {
    return globalListenerMapper
        .toGlobalExecutionListenerUpdateRequest(id, request)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateGlobalListener);
  }

  @CamundaDeleteMapping(path = "/{id}")
  public CompletableFuture<ResponseEntity<Object>> deleteGlobalExecutionListener(
      @PathVariable("id") final String id) {
    return globalListenerMapper
        .toGlobalExecutionListenerDeleteRequest(id)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::deleteGlobalListener);
  }

  private CompletableFuture<ResponseEntity<Object>> createGlobalListener(
      final GlobalListenerRecord request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> globalListenerServices.createGlobalListener(request, authentication),
        globalListenerMapper::toGlobalExecutionListenerResponse,
        HttpStatus.CREATED);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  private ResponseEntity<Object> search(
      @RequestBody(required = false) final GlobalExecutionListenerSearchQueryRequest request) {
    return SearchQueryRequestMapper.toGlobalExecutionListenerQuery(request)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<Object> getGlobalListener(final GlobalListenerRecord request) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toGlobalExecutionListenerResult(
                  globalListenerServices.getGlobalExecutionListener(request, authentication)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> updateGlobalListener(
      final GlobalListenerRecord request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> globalListenerServices.updateGlobalListener(request, authentication),
        globalListenerMapper::toGlobalExecutionListenerResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> deleteGlobalListener(
      final GlobalListenerRecord request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> globalListenerServices.deleteGlobalListener(request, authentication));
  }

  private ResponseEntity<Object> search(final GlobalListenerQuery query) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = globalListenerServices.search(query, authentication);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toGlobalExecutionListenerSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
