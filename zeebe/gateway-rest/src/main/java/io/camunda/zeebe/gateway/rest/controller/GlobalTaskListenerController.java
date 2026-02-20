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
@RequestMapping("/v2/global-task-listeners")
public class GlobalTaskListenerController {

  private final GlobalListenerServices globalListenerServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final GlobalListenerMapper globalListenerMapper;

  public GlobalTaskListenerController(
      final GlobalListenerServices globalListenerServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final IdentifierValidator identifierValidator) {
    this.globalListenerServices = globalListenerServices;
    this.authenticationProvider = authenticationProvider;
    globalListenerMapper =
        new GlobalListenerMapper(new GlobalListenerRequestValidator(identifierValidator));
  }

  @CamundaPostMapping()
  public CompletableFuture<ResponseEntity<Object>> createGlobalTaskListener(
      @RequestBody final CreateGlobalTaskListenerRequest request) {
    return globalListenerMapper
        .toGlobalTaskListenerCreateRequest(request)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createGlobalListener);
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{id}")
  public ResponseEntity<Object> getGlobalTaskListener(@PathVariable("id") final String id) {
    return globalListenerMapper
        .toGlobalTaskListenerGetRequest(id)
        .fold(RestErrorMapper::mapProblemToResponse, this::getGlobalListener);
  }

  @CamundaPutMapping(path = "/{id}")
  public CompletableFuture<ResponseEntity<Object>> updateGlobalTaskListener(
      @PathVariable("id") final String id,
      @RequestBody final UpdateGlobalTaskListenerRequest request) {
    return globalListenerMapper
        .toGlobalTaskListenerUpdateRequest(id, request)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateGlobalListener);
  }

  @CamundaDeleteMapping(path = "/{id}")
  public CompletableFuture<ResponseEntity<Object>> deleteGlobalTaskListener(
      @PathVariable("id") final String id) {
    return globalListenerMapper
        .toGlobalTaskListenerDeleteRequest(id)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::deleteGlobalListener);
  }

  private CompletableFuture<ResponseEntity<Object>> createGlobalListener(
      final GlobalListenerRecord request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            globalListenerServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createGlobalListener(request),
        globalListenerMapper::toGlobalListenerResponse,
        HttpStatus.CREATED);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  private ResponseEntity<Object> search(
      @RequestBody(required = false) final GlobalTaskListenerSearchQueryRequest request) {
    return SearchQueryRequestMapper.toGlobalTaskListenerQuery(request)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<Object> getGlobalListener(final GlobalListenerRecord request) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toGlobalTaskListenerResult(
                  globalListenerServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getGlobalTaskListener(request)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> updateGlobalListener(
      final GlobalListenerRecord request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            globalListenerServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .updateGlobalListener(request),
        globalListenerMapper::toGlobalListenerResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> deleteGlobalListener(
      final GlobalListenerRecord request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            globalListenerServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteGlobalListener(request));
  }

  private ResponseEntity<Object> search(final GlobalListenerQuery query) {
    try {
      final var result =
          globalListenerServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toGlobalTaskListenerSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
