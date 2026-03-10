/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication.adapter;

import io.camunda.auth.domain.model.AuthMappingRule;
import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.port.inbound.MappingRuleManagementPort;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.MappingRuleServices.MappingRuleDTO;
import java.util.concurrent.CompletionException;

/**
 * Bridges the auth library's {@link MappingRuleManagementPort} to the monorepo's {@link
 * MappingRuleServices}. OIDC claim-to-identity mapping rules are managed via Zeebe commands and
 * queried from the search infrastructure.
 */
public class OcMappingRuleManagementAdapter implements MappingRuleManagementPort {

  private final MappingRuleServices mappingRuleServices;
  private final CamundaAuthenticationProvider authProvider;

  public OcMappingRuleManagementAdapter(
      final MappingRuleServices mappingRuleServices,
      final CamundaAuthenticationProvider authProvider) {
    this.mappingRuleServices = mappingRuleServices;
    this.authProvider = authProvider;
  }

  @Override
  public AuthMappingRule getById(final String mappingRuleId) {
    final var entity = mappingRuleServices.withAuthentication(auth()).getMappingRule(mappingRuleId);
    return toAuthMappingRule(entity);
  }

  @Override
  public AuthMappingRule create(
      final String mappingRuleId,
      final String claimName,
      final String claimValue,
      final String name) {
    try {
      mappingRuleServices
          .withAuthentication(auth())
          .createMappingRule(new MappingRuleDTO(claimName, claimValue, name, mappingRuleId))
          .join();
      return new AuthMappingRule(0L, mappingRuleId, claimName, claimValue, name);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public AuthMappingRule update(
      final String mappingRuleId,
      final String claimName,
      final String claimValue,
      final String name) {
    try {
      mappingRuleServices
          .withAuthentication(auth())
          .updateMappingRule(new MappingRuleDTO(claimName, claimValue, name, mappingRuleId))
          .join();
      return new AuthMappingRule(0L, mappingRuleId, claimName, claimValue, name);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void delete(final String mappingRuleId) {
    try {
      mappingRuleServices.withAuthentication(auth()).deleteMappingRule(mappingRuleId).join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  private CamundaAuthentication auth() {
    return authProvider.getCamundaAuthentication();
  }

  private static AuthMappingRule toAuthMappingRule(final MappingRuleEntity entity) {
    return new AuthMappingRule(
        entity.mappingRuleKey() != null ? entity.mappingRuleKey() : 0L,
        entity.mappingRuleId(),
        entity.claimName(),
        entity.claimValue(),
        entity.name());
  }

  private static RuntimeException mapException(final Throwable cause) {
    if (cause instanceof RuntimeException re) {
      return re;
    }
    return new RuntimeException(cause);
  }
}
