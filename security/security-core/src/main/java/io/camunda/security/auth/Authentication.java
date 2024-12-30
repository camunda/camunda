/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static java.util.Collections.unmodifiableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class Authentication {
  private final Long authenticatedUserKey;
  private final List<Long> authenticatedGroupKeys;
  private final List<Long> authenticatedRoleKeys;
  private final List<String> authenticatedTenantIds;
  private final String token;

  public Authentication(
      final Long authenticatedUserKey,
      final List<Long> authenticatedGroupKeys,
      final List<Long> authenticatedRoleKeys,
      final List<String> authenticatedTenantIds,
      final String token) {
    this.authenticatedUserKey = authenticatedUserKey;
    this.authenticatedGroupKeys = authenticatedGroupKeys;
    this.authenticatedRoleKeys = authenticatedRoleKeys;
    this.authenticatedTenantIds = authenticatedTenantIds;
    this.token = token;
  }

  public static Authentication none() {
    return new Builder().build();
  }

  public static Authentication of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public Long authenticatedUserKey() {
    return authenticatedUserKey;
  }

  public List<Long> authenticatedGroupKeys() {
    return authenticatedGroupKeys;
  }

  public List<Long> authenticatedRoleKeys() {
    return authenticatedRoleKeys;
  }

  public List<String> authenticatedTenantIds() {
    return authenticatedTenantIds;
  }

  public String token() {
    return token;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        authenticatedUserKey,
        authenticatedGroupKeys,
        authenticatedRoleKeys,
        authenticatedTenantIds,
        token);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final Authentication that = (Authentication) obj;
    return Objects.equals(authenticatedUserKey, that.authenticatedUserKey)
        && Objects.equals(authenticatedGroupKeys, that.authenticatedGroupKeys)
        && Objects.equals(authenticatedRoleKeys, that.authenticatedRoleKeys)
        && Objects.equals(authenticatedTenantIds, that.authenticatedTenantIds)
        && Objects.equals(token, that.token);
  }

  @Override
  public String toString() {
    return "Authentication["
        + "authenticatedUserKey="
        + authenticatedUserKey
        + ", "
        + "authenticatedGroupKeys="
        + authenticatedGroupKeys
        + ", "
        + "authenticatedRoleKeys="
        + authenticatedRoleKeys
        + ", "
        + "authenticatedTenantIds="
        + authenticatedTenantIds
        + ", "
        + "token="
        + token
        + ']';
  }

  public static final class Builder {

    private Long userKey;
    private final List<Long> groupKeys = new ArrayList<>();
    private final List<Long> roleKeys = new ArrayList<>();
    private final List<String> tenants = new ArrayList<>();
    private String token;

    public Builder user(final Long value) {
      userKey = value;
      return this;
    }

    public Builder group(final Long value) {
      return groupKeys(Collections.singletonList(value));
    }

    public Builder groupKeys(final List<Long> values) {
      if (values != null) {
        groupKeys.addAll(values);
      }
      return this;
    }

    public Builder role(final Long value) {
      return roleKeys(Collections.singletonList(value));
    }

    public Builder roleKeys(final List<Long> values) {
      if (values != null) {
        roleKeys.addAll(values);
      }
      return this;
    }

    public Builder tenant(final String tenant) {
      return tenants(Collections.singletonList(tenant));
    }

    public Builder tenants(final List<String> values) {
      if (values != null) {
        tenants.addAll(values);
      }
      return this;
    }

    public Builder token(final String value) {
      token = value;
      return this;
    }

    public Authentication build() {
      return new Authentication(
          userKey,
          unmodifiableList(groupKeys),
          unmodifiableList(roleKeys),
          unmodifiableList(tenants),
          token);
    }
  }
}
