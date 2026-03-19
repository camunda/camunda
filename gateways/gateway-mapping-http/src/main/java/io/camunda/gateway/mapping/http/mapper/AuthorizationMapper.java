/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuthorizationIdBasedRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuthorizationPropertyBasedRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuthorizationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedPermissionTypeEnum;
import io.camunda.gateway.mapping.http.validator.AuthorizationRequestValidator;
import io.camunda.gateway.protocol.model.AuthorizationIdBasedRequest;
import io.camunda.gateway.protocol.model.AuthorizationPropertyBasedRequest;
import io.camunda.gateway.protocol.model.AuthorizationRequest;
import io.camunda.gateway.protocol.model.PermissionTypeEnum;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.AuthorizationServices.UpdateAuthorizationRequest;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public class AuthorizationMapper {

  private final AuthorizationRequestValidator authorizationRequestValidator;

  public AuthorizationMapper(final AuthorizationRequestValidator authorizationRequestValidator) {
    this.authorizationRequestValidator = authorizationRequestValidator;
  }

  public Either<ProblemDetail, CreateAuthorizationRequest> toCreateAuthorizationRequest(
      final AuthorizationRequest request) {
    return switch (request) {
      case final AuthorizationIdBasedRequest idReq -> toCreateAuthorizationRequest(idReq);
      case final AuthorizationPropertyBasedRequest propReq -> toCreateAuthorizationRequest(propReq);
      default -> Either.left(createUnsupportedAuthorizationProblemDetail(request));
    };
  }

  public Either<ProblemDetail, CreateAuthorizationRequest> toCreateAuthorizationRequest(
      final AuthorizationIdBasedRequest request) {
    return RequestMapper.getResult(
        authorizationRequestValidator.validateIdBasedRequest(request),
        () ->
            new CreateAuthorizationRequest(
                request.getOwnerId(),
                AuthorizationOwnerType.valueOf(request.getOwnerType().name()),
                resolveIdBasedResourceMatcher(request.getResourceId()),
                request.getResourceId(),
                "",
                AuthorizationResourceType.valueOf(request.getResourceType().name()),
                transformPermissionTypes(request.getPermissionTypes())));
  }

  public Either<ProblemDetail, CreateAuthorizationRequest> toCreateAuthorizationRequest(
      final AuthorizationPropertyBasedRequest request) {

    return RequestMapper.getResult(
        authorizationRequestValidator.validatePropertyBasedRequest(request),
        () ->
            new CreateAuthorizationRequest(
                request.getOwnerId(),
                AuthorizationOwnerType.valueOf(request.getOwnerType().name()),
                AuthorizationResourceMatcher.PROPERTY,
                "",
                request.getResourcePropertyName(),
                AuthorizationResourceType.valueOf(request.getResourceType().name()),
                transformPermissionTypes(request.getPermissionTypes())));
  }

  public Either<ProblemDetail, UpdateAuthorizationRequest> toUpdateAuthorizationRequest(
      final long authorizationKey, final AuthorizationRequest request) {
    return switch (request) {
      case final AuthorizationIdBasedRequest idReq ->
          toUpdateAuthorizationRequest(authorizationKey, idReq);
      case final AuthorizationPropertyBasedRequest propReq ->
          toUpdateAuthorizationRequest(authorizationKey, propReq);
      default -> Either.left(createUnsupportedAuthorizationProblemDetail(request));
    };
  }

  public Either<ProblemDetail, UpdateAuthorizationRequest> toUpdateAuthorizationRequest(
      final long authorizationKey, final AuthorizationIdBasedRequest request) {
    return RequestMapper.getResult(
        authorizationRequestValidator.validateIdBasedRequest(request),
        () ->
            new UpdateAuthorizationRequest(
                authorizationKey,
                request.getOwnerId(),
                AuthorizationOwnerType.valueOf(request.getOwnerType().name()),
                resolveIdBasedResourceMatcher(request.getResourceId()),
                request.getResourceId(),
                "",
                AuthorizationResourceType.valueOf(request.getResourceType().name()),
                transformPermissionTypes(request.getPermissionTypes())));
  }

  public Either<ProblemDetail, UpdateAuthorizationRequest> toUpdateAuthorizationRequest(
      final long authorizationKey, final AuthorizationPropertyBasedRequest request) {
    return RequestMapper.getResult(
        authorizationRequestValidator.validatePropertyBasedRequest(request),
        () ->
            new UpdateAuthorizationRequest(
                authorizationKey,
                request.getOwnerId(),
                AuthorizationOwnerType.valueOf(request.getOwnerType().name()),
                AuthorizationResourceMatcher.PROPERTY,
                "",
                request.getResourcePropertyName(),
                AuthorizationResourceType.valueOf(request.getResourceType().name()),
                transformPermissionTypes(request.getPermissionTypes())));
  }

  private static AuthorizationResourceMatcher resolveIdBasedResourceMatcher(
      final String resourceId) {
    return AuthorizationScope.WILDCARD.getResourceId().equals(resourceId)
        ? AuthorizationResourceMatcher.ANY
        : AuthorizationResourceMatcher.ID;
  }

  // --- Strict contract overloads ---

  public Either<ProblemDetail, CreateAuthorizationRequest> toCreateAuthorizationRequest(
      final GeneratedAuthorizationRequestStrictContract request) {
    return switch (request) {
      case GeneratedAuthorizationIdBasedRequestStrictContract idReq ->
          toCreateAuthorizationRequest(idReq);
      case GeneratedAuthorizationPropertyBasedRequestStrictContract propReq ->
          toCreateAuthorizationRequest(propReq);
    };
  }

  public Either<ProblemDetail, CreateAuthorizationRequest> toCreateAuthorizationRequest(
      final GeneratedAuthorizationIdBasedRequestStrictContract request) {
    return RequestMapper.getResult(
        authorizationRequestValidator.validateIdBasedRequest(request),
        () ->
            new CreateAuthorizationRequest(
                request.ownerId(),
                AuthorizationOwnerType.valueOf(request.ownerType().name()),
                resolveIdBasedResourceMatcher(request.resourceId()),
                request.resourceId(),
                "",
                AuthorizationResourceType.valueOf(request.resourceType().name()),
                transformStrictPermissionTypes(request.permissionTypes())));
  }

  public Either<ProblemDetail, CreateAuthorizationRequest> toCreateAuthorizationRequest(
      final GeneratedAuthorizationPropertyBasedRequestStrictContract request) {
    return RequestMapper.getResult(
        authorizationRequestValidator.validatePropertyBasedRequest(request),
        () ->
            new CreateAuthorizationRequest(
                request.ownerId(),
                AuthorizationOwnerType.valueOf(request.ownerType().name()),
                AuthorizationResourceMatcher.PROPERTY,
                "",
                request.resourcePropertyName(),
                AuthorizationResourceType.valueOf(request.resourceType().name()),
                transformStrictPermissionTypes(request.permissionTypes())));
  }

  public Either<ProblemDetail, UpdateAuthorizationRequest> toUpdateAuthorizationRequest(
      final long authorizationKey, final GeneratedAuthorizationRequestStrictContract request) {
    return switch (request) {
      case GeneratedAuthorizationIdBasedRequestStrictContract idReq ->
          toUpdateAuthorizationRequest(authorizationKey, idReq);
      case GeneratedAuthorizationPropertyBasedRequestStrictContract propReq ->
          toUpdateAuthorizationRequest(authorizationKey, propReq);
    };
  }

  public Either<ProblemDetail, UpdateAuthorizationRequest> toUpdateAuthorizationRequest(
      final long authorizationKey,
      final GeneratedAuthorizationIdBasedRequestStrictContract request) {
    return RequestMapper.getResult(
        authorizationRequestValidator.validateIdBasedRequest(request),
        () ->
            new UpdateAuthorizationRequest(
                authorizationKey,
                request.ownerId(),
                AuthorizationOwnerType.valueOf(request.ownerType().name()),
                resolveIdBasedResourceMatcher(request.resourceId()),
                request.resourceId(),
                "",
                AuthorizationResourceType.valueOf(request.resourceType().name()),
                transformStrictPermissionTypes(request.permissionTypes())));
  }

  public Either<ProblemDetail, UpdateAuthorizationRequest> toUpdateAuthorizationRequest(
      final long authorizationKey,
      final GeneratedAuthorizationPropertyBasedRequestStrictContract request) {
    return RequestMapper.getResult(
        authorizationRequestValidator.validatePropertyBasedRequest(request),
        () ->
            new UpdateAuthorizationRequest(
                authorizationKey,
                request.ownerId(),
                AuthorizationOwnerType.valueOf(request.ownerType().name()),
                AuthorizationResourceMatcher.PROPERTY,
                "",
                request.resourcePropertyName(),
                AuthorizationResourceType.valueOf(request.resourceType().name()),
                transformStrictPermissionTypes(request.permissionTypes())));
  }

  private static Set<PermissionType> transformStrictPermissionTypes(
      final List<GeneratedPermissionTypeEnum> permissionTypes) {
    return permissionTypes.stream()
        .map(permission -> PermissionType.valueOf(permission.name()))
        .collect(Collectors.toSet());
  }

  private static ProblemDetail createUnsupportedAuthorizationProblemDetail(
      final AuthorizationRequest request) {
    return GatewayErrorMapper.createProblemDetail(
        HttpStatus.BAD_REQUEST,
        "Unsupported authorization request: " + request.getClass().getSimpleName(),
        "Only authorization by id or property is supported.");
  }

  private static Set<PermissionType> transformPermissionTypes(
      final List<PermissionTypeEnum> permissionTypes) {
    return permissionTypes.stream()
        .map(permission -> PermissionType.valueOf(permission.name()))
        .collect(Collectors.toSet());
  }
}
