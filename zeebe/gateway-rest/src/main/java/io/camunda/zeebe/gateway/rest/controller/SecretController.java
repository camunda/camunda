/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.validator.SecretRequestValidator;
import io.camunda.gateway.protocol.model.AdvancedSecretStatusFilter;
import io.camunda.gateway.protocol.model.SecretListRequest;
import io.camunda.gateway.protocol.model.SecretResolveRequest;
import io.camunda.gateway.protocol.model.SecretStatusEnum;
import io.camunda.secretstore.SecretStatus;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/secrets")
public class SecretController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public SecretController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/resolve")
  public CompletableFuture<ResponseEntity<Object>> resolveSecrets(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final SecretResolveRequest request) {
    return SecretRequestValidator.validateSecretResolveRequest(request)
        .<CompletableFuture<ResponseEntity<Object>>>map(
            RestErrorMapper::mapProblemToCompletedResponse)
        .orElseGet(() -> resolve(physicalTenantId, request.getReferences()));
  }

  private CompletableFuture<ResponseEntity<Object>> resolve(
      final String physicalTenantId, final List<String> references) {
    final var secretServices = serviceRegistry.secretServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> secretServices.resolve(references, authentication),
        ResponseMapper::toSecretResolveResult,
        HttpStatus.OK);
  }

  @CamundaPostMapping(path = "/list")
  public CompletableFuture<ResponseEntity<Object>> listSecrets(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final @Nullable SecretListRequest request) {
    return SecretRequestValidator.validateSecretListRequest(request)
        .<CompletableFuture<ResponseEntity<Object>>>map(
            RestErrorMapper::mapProblemToCompletedResponse)
        .orElseGet(() -> list(physicalTenantId, toSecretStatuses(request)));
  }

  private CompletableFuture<ResponseEntity<Object>> list(
      final String physicalTenantId, final @Nullable Set<SecretStatus> statuses) {
    final var secretServices = serviceRegistry.secretServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> secretServices.list(authentication, statuses),
        ResponseMapper::toSecretListResult,
        HttpStatus.OK);
  }

  /**
   * Resolves the status filter to the statuses it matches, or {@code null} for no filter. Every
   * operator narrows the set, so operators given together must all match, as in the search
   * endpoints. An empty {@code $in} or {@code $notIn} adds no constraint, since the model cannot
   * tell it apart from an absent one.
   */
  private static @Nullable Set<SecretStatus> toSecretStatuses(
      final @Nullable SecretListRequest request) {
    final var filter = request == null ? null : request.getFilter();
    final var status = filter == null ? null : filter.getStatus();
    if (status == null) {
      return null;
    }
    if (!(status instanceof final AdvancedSecretStatusFilter advanced)) {
      // the registered deserializer turns a plain value into {"$eq": value}
      throw new IllegalStateException(
          "Unexpected status filter type: " + status.getClass().getName());
    }
    final var statuses = EnumSet.allOf(SecretStatus.class);
    if (advanced.get$Eq() != null) {
      statuses.retainAll(Set.of(toSecretStatus(advanced.get$Eq())));
    }
    if (advanced.get$Neq() != null) {
      statuses.remove(toSecretStatus(advanced.get$Neq()));
    }
    if (Boolean.FALSE.equals(advanced.get$Exists())) {
      // every listed secret has a status, so none lacks one
      statuses.clear();
    }
    if (!advanced.get$In().isEmpty()) {
      statuses.retainAll(toSecretStatusSet(advanced.get$In()));
    }
    if (!advanced.get$NotIn().isEmpty()) {
      statuses.removeAll(toSecretStatusSet(advanced.get$NotIn()));
    }
    if (advanced.get$Like() != null) {
      final var pattern = likeToPattern(advanced.get$Like());
      statuses.removeIf(candidate -> !pattern.matcher(candidate.name()).matches());
    }
    return statuses;
  }

  private static Set<SecretStatus> toSecretStatusSet(final List<SecretStatusEnum> values) {
    return values.stream()
        .map(SecretController::toSecretStatus)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(SecretStatus.class)));
  }

  private static SecretStatus toSecretStatus(final SecretStatusEnum status) {
    return switch (status) {
      case ACTIVE -> SecretStatus.ACTIVE;
      case DISABLED -> SecretStatus.DISABLED;
      case EXPIRED -> SecretStatus.EXPIRED;
      case NOT_YET_ACTIVE -> SecretStatus.NOT_YET_ACTIVE;
    };
  }

  /**
   * Turns a {@code $like} value into a pattern: {@code *} matches any run of characters, {@code ?}
   * one character, and a backslash makes the next character literal.
   */
  private static Pattern likeToPattern(final String like) {
    final var regex = new StringBuilder();
    for (int i = 0; i < like.length(); i++) {
      final char c = like.charAt(i);
      if (c == '\\' && i + 1 < like.length()) {
        regex.append(Pattern.quote(String.valueOf(like.charAt(++i))));
      } else if (c == '*') {
        regex.append(".*");
      } else if (c == '?') {
        regex.append('.');
      } else {
        regex.append(Pattern.quote(String.valueOf(c)));
      }
    }
    return Pattern.compile(regex.toString());
  }
}
