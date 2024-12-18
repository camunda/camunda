/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.service;

import io.camunda.search.entities.RoleEntity;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.security.auth.Authentication;
import io.camunda.service.RoleServices;
import java.util.Collections;
import org.springframework.stereotype.Component;

@Component
public class RoleService {

  private final RoleServices roleServices;

  public RoleService(
      final RoleServices roleServices, final Authentication.Builder servicesAuthenticationBuilder) {
    this.roleServices = roleServices.withAuthentication(servicesAuthenticationBuilder.build());
  }

  public RoleEntity create(final String name) {
    final var create = roleServices.createRole(name).join();
    return new RoleEntity(create.getRoleKey(), name, Collections.emptySet());
  }

  public RoleEntity fetch(final String name) {

    final var roles =
        roleServices.search(
            SearchQueryBuilders.roleSearchQuery(
                fn -> fn.filter(f -> f.name(name)).page(p -> p.size(1))));
    if (roles.total() == 0) {
      throw new RuntimeException("No tenant found with id " + roles);
    } else {
      return roles.items().getFirst();
    }
  }

  public RoleEntity createOrFetch(final String name) {
    try {
      return fetch(name);
    } catch (final RuntimeException e) {
      return create(name);
    }
  }
}
