/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.mapper.AuthorizationMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuthorizationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuthorizationSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.validator.AuthorizationRequestValidator;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.validation.AuthorizationValidator;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.AuthorizationServices;
import io.camunda.zeebe.gateway.rest.controller.generated.AuthorizationServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultAuthorizationServiceAdapter implements AuthorizationServiceAdapter {

  private final AuthorizationServices authorizationServices;
  private final AuthorizationMapper authorizationMapper;

  public DefaultAuthorizationServiceAdapter(
      final AuthorizationServices authorizationServices,
      final IdentifierValidator identifierValidator) {
    this.authorizationServices = authorizationServices;
    authorizationMapper =
        new AuthorizationMapper(
            new AuthorizationRequestValidator(new AuthorizationValidator(identifierValidator)));
  }

  @Override
  public ResponseEntity<Object> createAuthorization(
      final GeneratedAuthorizationRequestStrictContract authorizationRequest,
      final CamundaAuthentication authentication) {
    return authorizationMapper
        .toCreateAuthorizationRequest(authorizationRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            converted ->
                RequestExecutor.executeSync(
                    () -> authorizationServices.createAuthorization(converted, authentication),
                    ResponseMapper::toAuthorizationCreateResponse,
                    HttpStatus.CREATED));
  }

  @Override
  public ResponseEntity<Void> updateAuthorization(
      final Long authorizationKey,
      final GeneratedAuthorizationRequestStrictContract authorizationRequest,
      final CamundaAuthentication authentication) {
    return authorizationMapper
        .toUpdateAuthorizationRequest(authorizationKey, authorizationRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            converted ->
                RequestExecutor.executeSync(
                    () -> authorizationServices.updateAuthorization(converted, authentication)));
  }

  @Override
  public ResponseEntity<Object> getAuthorization(
      final Long authorizationKey, final CamundaAuthentication authentication) {
    try {
      final var result = authorizationServices.getAuthorization(authorizationKey, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toAuthorization(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Void> deleteAuthorization(
      final Long authorizationKey, final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        () -> authorizationServices.deleteAuthorization(authorizationKey, authentication));
  }

  @Override
  public ResponseEntity<Object> searchAuthorizations(
      final GeneratedAuthorizationSearchQueryRequestStrictContract authorizationSearchQueryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toAuthorizationQueryStrict(authorizationSearchQueryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = authorizationServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toAuthorizationSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }
}
