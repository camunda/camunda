/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.service;

import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.security.auth.Authentication;
import io.camunda.service.MappingServices;
import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.stereotype.Service;

@Service
public class MappingService {

  private static final String USERNAME_CLAIM = "sub";

  private final MappingServices mappingServices;
  private final ZeebeClient zeebeClient;

  public MappingService(
      final MappingServices mappingServices,
      final ZeebeClient zeebeClient,
      final Authentication.Builder servicesAuthenticationBuilder) {
    this.mappingServices =
        mappingServices.withAuthentication(servicesAuthenticationBuilder.build());
    this.zeebeClient = zeebeClient;
  }

  public Long findOrCreateUserWithUsername(final String username) {
    return findOrCreateMapping(username + "_mapping", USERNAME_CLAIM, username);
  }

  public Long findOrCreateMapping(
      final String name, final String claimName, final String claimValue) {
    final Long mappingKey;
    final var mappings =
        mappingServices.search(
            SearchQueryBuilders.mappingSearchQuery(
                fn ->
                    fn.filter(f -> f.claimName(claimName).claimValue(claimValue))
                        .page(p -> p.size(1))));
    if (mappings.total() == 0) {
      // Name should be added
      mappingKey =
          zeebeClient
              .newCreateMappingCommand()
              .claimName(claimName)
              .claimValue(claimValue)
              .send()
              .join()
              .getMappingKey();

    } else {
      mappingKey = mappings.items().getFirst().mappingKey();
    }
    return mappingKey;
  }
}
