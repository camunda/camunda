/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.security.auth;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.addValuesToSet;

import io.camunda.service.search.filter.FilterBase;
import io.camunda.util.ObjectBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public record Authentication(
    String authenticatedUserId,
    List<String> authenticatedGroupIds,
    Set<String> authenticatedTenantIds,
    String token)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<Authentication> {

    private String user;
    private List<String> groups;
    private Set<String> tenants;
    private String token;

    public Builder user(final String value) {
      user = value;
      return this;
    }

    public Builder group(final String value) {
      return groups(List.of(value));
    }

    public Builder groups(final List<String> values) {
      groups = addValuesToList(groups, values);
      return this;
    }

    public Builder tenant(final String tenant) {
      return tenants(Set.of(tenant));
    }

    public Builder tenants(final Collection<String> values) {
      tenants = addValuesToSet(tenants, values);
      return this;
    }

    public Builder token(final String value) {
      token = value;
      return this;
    }

    @Override
    public Authentication build() {
      return new Authentication(user, groups, tenants, token);
    }
  }
}
