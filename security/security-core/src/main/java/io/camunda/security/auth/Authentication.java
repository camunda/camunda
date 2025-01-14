/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static java.util.Collections.unmodifiableList;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class Authentication {
  private final Long authenticatedUserKey;
  private final List<Long> authenticatedGroupKeys;
  private final List<Long> authenticatedRoleKeys;
  private final List<String> authenticatedTenantIds;
  private final Map<String, Object> claims;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public Authentication(
      final @JsonProperty("authenticated_user_key") Long authenticatedUserKey,
      final @JsonProperty("authenticated_group_keys") List<Long> authenticatedGroupKeys,
      final @JsonProperty("authenticated_role_keys") List<Long> authenticatedRoleKeys,
      final @JsonProperty("authenticated_tenant_ids") List<String> authenticatedTenantIds,
      final @JsonProperty("claims") Map<String, Object> claims) {
    this.authenticatedUserKey = authenticatedUserKey;
    this.authenticatedGroupKeys = authenticatedGroupKeys;
    this.authenticatedRoleKeys = authenticatedRoleKeys;
    this.authenticatedTenantIds = authenticatedTenantIds;
    this.claims = claims;
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

  public Map<String, Object> claims() {
    return claims;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        authenticatedUserKey,
        authenticatedGroupKeys,
        authenticatedRoleKeys,
        authenticatedTenantIds,
        claims);
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
        && Objects.equals(claims, that.claims);
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
        + "claims="
        + claims
        + ']';
  }

  public static final class Builder {

    private Long userKey;
    private final List<Long> groupKeys = new ArrayList<>();
    private final List<Long> roleKeys = new ArrayList<>();
    private final List<String> tenants = new ArrayList<>();
    private Map<String, Object> claims;

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

    public Builder claims(final Map<String, Object> value) {
      claims = value;
      return this;
    }

    public Authentication build() {
      return new Authentication(
          userKey,
          unmodifiableList(groupKeys),
          unmodifiableList(roleKeys),
          unmodifiableList(tenants),
          claims);
    }
  }
}
