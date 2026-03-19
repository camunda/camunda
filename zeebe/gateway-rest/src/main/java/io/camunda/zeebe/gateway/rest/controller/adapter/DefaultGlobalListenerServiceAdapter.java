/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.mapper.GlobalListenerMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedCreateGlobalTaskListenerRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGlobalTaskListenerSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUpdateGlobalTaskListenerRequestStrictContract;
import io.camunda.gateway.mapping.http.validator.GlobalListenerRequestValidator;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.GlobalListenerServices;
import io.camunda.zeebe.gateway.rest.controller.generated.GlobalListenerServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultGlobalListenerServiceAdapter implements GlobalListenerServiceAdapter {

  private final GlobalListenerServices globalListenerServices;
  private final GlobalListenerMapper globalListenerMapper;

  public DefaultGlobalListenerServiceAdapter(
      final GlobalListenerServices globalListenerServices,
      final IdentifierValidator identifierValidator) {
    this.globalListenerServices = globalListenerServices;
    globalListenerMapper =
        new GlobalListenerMapper(new GlobalListenerRequestValidator(identifierValidator));
  }

  @Override
  public ResponseEntity<Object> createGlobalTaskListener(
      final GeneratedCreateGlobalTaskListenerRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    return globalListenerMapper
        .toGlobalTaskListenerCreateRequest(requestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                RequestExecutor.executeSync(
                    () -> globalListenerServices.createGlobalListener(mapped, authentication),
                    globalListenerMapper::toGlobalListenerResponse,
                    HttpStatus.CREATED));
  }

  @Override
  public ResponseEntity<Object> getGlobalTaskListener(
      final String id, final CamundaAuthentication authentication) {
    return globalListenerMapper
        .toGlobalTaskListenerGetRequest(id)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped -> {
              try {
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toGlobalTaskListenerResult(
                        globalListenerServices.getGlobalTaskListener(mapped, authentication)));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> updateGlobalTaskListener(
      final String id,
      final GeneratedUpdateGlobalTaskListenerRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    return globalListenerMapper
        .toGlobalTaskListenerUpdateRequest(id, requestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                RequestExecutor.executeSync(
                    () -> globalListenerServices.updateGlobalListener(mapped, authentication),
                    globalListenerMapper::toGlobalListenerResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Void> deleteGlobalTaskListener(
      final String id, final CamundaAuthentication authentication) {
    return globalListenerMapper
        .toGlobalTaskListenerDeleteRequest(id)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                RequestExecutor.executeSync(
                    () -> globalListenerServices.deleteGlobalListener(mapped, authentication)));
  }

  @Override
  public ResponseEntity<Object> searchGlobalTaskListeners(
      final GeneratedGlobalTaskListenerSearchQueryRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toGlobalTaskListenerQueryStrict(requestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = globalListenerServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toGlobalTaskListenerSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }
}
