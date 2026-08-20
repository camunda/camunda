/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.validator.UserRequestValidator;
import io.camunda.gateway.protocol.model.UserRequest;
import io.camunda.gateway.protocol.model.UserUpdateRequest;
import io.camunda.service.UserServices.UserDTO;
import io.camunda.zeebe.util.Either;
import org.springframework.http.ProblemDetail;

public class UserMapper {

  private final UserRequestValidator userRequestValidator;

  public UserMapper(final UserRequestValidator userRequestValidator) {
    this.userRequestValidator = userRequestValidator;
  }

  public Either<ProblemDetail, UserDTO> toUserRequest(final UserRequest request) {
    return RequestMapper.getResult(
        userRequestValidator.validateCreateRequest(request),
        () ->
            new UserDTO(
                request.getUsername(),
                request.getName(),
                request.getEmail(),
                request.getPassword()));
  }

  public Either<ProblemDetail, UserDTO> toUserUpdateRequest(
      final UserUpdateRequest updateRequest, final String username) {
    return RequestMapper.getResult(
        userRequestValidator.validateUpdateRequest(updateRequest),
        () ->
            new UserDTO(
                username,
                updateRequest.getName(),
                updateRequest.getEmail(),
                updateRequest.getPassword()));
  }
}
