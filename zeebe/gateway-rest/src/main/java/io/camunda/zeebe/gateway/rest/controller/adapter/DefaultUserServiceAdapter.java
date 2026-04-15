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
import io.camunda.gateway.mapping.http.mapper.UserMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.UserRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UserSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UserUpdateRequestContract;
import io.camunda.gateway.mapping.http.validator.UserRequestValidator;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.UserValidator;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.rest.controller.generated.UserServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultUserServiceAdapter implements UserServiceAdapter {

  private final UserServices userServices;
  private final UserMapper userMapper;

  public DefaultUserServiceAdapter(
      final UserServices userServices, final IdentifierValidator identifierValidator) {
    this.userServices = userServices;
    userMapper = new UserMapper(new UserRequestValidator(new UserValidator(identifierValidator)));
  }

  @Override
  public ResponseEntity<Object> createUser(
      final UserRequestContract userRequestStrict, final CamundaAuthentication authentication) {
    return userMapper
        .toUserRequest(userRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            converted ->
                RequestExecutor.executeSync(
                    () -> userServices.createUser(converted, authentication),
                    ResponseMapper::toUserCreateResponse,
                    HttpStatus.CREATED));
  }

  @Override
  public ResponseEntity<Object> searchUsers(
      final UserSearchQueryRequestContract userSearchQueryRequestStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toUserQueryStrict(userSearchQueryRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = userServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toUserSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getUser(
      final String username, final CamundaAuthentication authentication) {
    try {
      final var result = userServices.getUser(username, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toUser(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Object> updateUser(
      final String username,
      final UserUpdateRequestContract userUpdateRequestStrict,
      final CamundaAuthentication authentication) {
    return userMapper
        .toUserUpdateRequest(userUpdateRequestStrict, username)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            converted ->
                RequestExecutor.executeSync(
                    () -> userServices.updateUser(converted, authentication),
                    ResponseMapper::toUserUpdateResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Void> deleteUser(
      final String username, final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(() -> userServices.deleteUser(username, authentication));
  }
}
