/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.AuthorizationIdBasedRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.AuthorizationPropertyBasedRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.AuthorizationRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.PermissionTypeEnum;
import io.camunda.gateway.mapping.http.validator.AuthorizationRequestValidator;
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
import org.springframework.http.ProblemDetail;

public class AuthorizationMapper {

  private final AuthorizationRequestValidator authorizationRequestValidator;

  public AuthorizationMapper(final AuthorizationRequestValidator authorizationRequestValidator) {
    this.authorizationRequestValidator = authorizationRequestValidator;
  }

  private static AuthorizationResourceMatcher resolveIdBasedResourceMatcher(
      final String resourceId) {
    return AuthorizationScope.WILDCARD.getResourceId().equals(resourceId)
        ? AuthorizationResourceMatcher.ANY
        : AuthorizationResourceMatcher.ID;
  }

  public Either<ProblemDetail, CreateAuthorizationRequest> toCreateAuthorizationRequest(
      final AuthorizationRequestContract request) {
    return switch (request) {
      case AuthorizationIdBasedRequestContract idReq -> toCreateAuthorizationRequest(idReq);
      case AuthorizationPropertyBasedRequestContract propReq ->
          toCreateAuthorizationRequest(propReq);
    };
  }

  public Either<ProblemDetail, CreateAuthorizationRequest> toCreateAuthorizationRequest(
      final AuthorizationIdBasedRequestContract request) {
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
                transformPermissionTypes(request.permissionTypes())));
  }

  public Either<ProblemDetail, CreateAuthorizationRequest> toCreateAuthorizationRequest(
      final AuthorizationPropertyBasedRequestContract request) {
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
                transformPermissionTypes(request.permissionTypes())));
  }

  public Either<ProblemDetail, UpdateAuthorizationRequest> toUpdateAuthorizationRequest(
      final long authorizationKey, final AuthorizationRequestContract request) {
    return switch (request) {
      case AuthorizationIdBasedRequestContract idReq ->
          toUpdateAuthorizationRequest(authorizationKey, idReq);
      case AuthorizationPropertyBasedRequestContract propReq ->
          toUpdateAuthorizationRequest(authorizationKey, propReq);
    };
  }

  public Either<ProblemDetail, UpdateAuthorizationRequest> toUpdateAuthorizationRequest(
      final long authorizationKey, final AuthorizationIdBasedRequestContract request) {
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
                transformPermissionTypes(request.permissionTypes())));
  }

  public Either<ProblemDetail, UpdateAuthorizationRequest> toUpdateAuthorizationRequest(
      final long authorizationKey, final AuthorizationPropertyBasedRequestContract request) {
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
                transformPermissionTypes(request.permissionTypes())));
  }

  private static Set<PermissionType> transformPermissionTypes(
      final List<PermissionTypeEnum> permissionTypes) {
    return permissionTypes.stream()
        .map(permission -> PermissionType.valueOf(permission.name()))
        .collect(Collectors.toSet());
  }
}
