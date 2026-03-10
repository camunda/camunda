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
import io.camunda.auth.domain.model.search.MappingRuleFilter;
import io.camunda.auth.domain.model.search.SearchQuery;
import io.camunda.auth.domain.model.search.SearchResult;
import io.camunda.auth.domain.port.inbound.MappingRuleManagementPort;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.query.MappingRuleQuery;
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

  @Override
  public SearchResult<AuthMappingRule> search(final SearchQuery<MappingRuleFilter> query) {
    final var filter = query.filter();
    final var page = query.page();

    final var ocQuery =
        MappingRuleQuery.of(
            q -> {
              q.filter(
                  f -> {
                    if (filter != null) {
                      if (filter.mappingRuleId() != null) {
                        f.mappingRuleId(filter.mappingRuleId());
                      }
                      if (filter.claimName() != null) {
                        f.claimName(filter.claimName());
                      }
                      if (filter.claimValue() != null) {
                        f.claimValue(filter.claimValue());
                      }
                      if (filter.name() != null) {
                        f.name(filter.name());
                      }
                    }
                    return f;
                  });
              q.page(p -> p.from(page.from()).size(page.size()));
              return q;
            });

    final var result = mappingRuleServices.withAuthentication(auth()).search(ocQuery);
    final var items =
        result.items().stream().map(OcMappingRuleManagementAdapter::toAuthMappingRule).toList();
    return new SearchResult<>(items, result.total());
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
