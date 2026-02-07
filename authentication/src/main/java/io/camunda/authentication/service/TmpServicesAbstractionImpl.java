/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.search.query.TenantQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import java.util.List;

public class TmpServicesAbstractionImpl implements TmpServicesAbstraction {

  private final UserServices userServices;
  private final TenantServices tenantServices;

  public TmpServicesAbstractionImpl(
      final UserServices userServices, final TenantServices tenantServices) {
    this.userServices = userServices;
    this.tenantServices = tenantServices;
  }

  @Override
  public User getUser(final String username) {
    final var userEntity =
        userServices.withAuthentication(CamundaAuthentication.anonymous()).getUser(username);
    return new User(userEntity.username(), userEntity.email());
  }

  @Override
  public List<Tenant> getTenants(final List<String> tenantIds) {
    return tenantServices
        .withAuthentication(CamundaAuthentication.anonymous())
        .search(TenantQuery.of(q -> q.filter(f -> f.tenantIds(tenantIds)).unlimited()))
        .items()
        .stream()
        .map(t -> new Tenant(t.key(), t.tenantId(), t.name(), t.description()))
        .toList();
  }
}
