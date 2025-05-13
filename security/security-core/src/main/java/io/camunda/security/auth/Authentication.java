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
  private final String authenticatedUsername;
  private final String authenticatedClientId;
  private final List<Long> authenticatedGroupKeys;
  private final List<String> authenticatedRoleIds;
  private final List<String> authenticatedTenantIds;
  private final List<String> authenticatedMappingIds;
  private final Map<String, Object> claims;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public Authentication(
      final @JsonProperty("authenticated_username") String authenticatedUsername,
      final @JsonProperty("authenticated_client_id") String authenticatedClientId,
      final @JsonProperty("authenticated_group_keys") List<Long> authenticatedGroupKeys,
      final @JsonProperty("authenticated_role_ids") List<String> authenticatedRoleIds,
      final @JsonProperty("authenticated_tenant_ids") List<String> authenticatedTenantIds,
      final @JsonProperty("authenticated_mapping_ids") List<String> authenticatedMappingIds,
      final @JsonProperty("claims") Map<String, Object> claims) {
    this.authenticatedUsername = authenticatedUsername;
    this.authenticatedClientId = authenticatedClientId;
    this.authenticatedGroupKeys = authenticatedGroupKeys;
    this.authenticatedRoleIds = authenticatedRoleIds;
    this.authenticatedTenantIds = authenticatedTenantIds;
    this.authenticatedMappingIds = authenticatedMappingIds;
    this.claims = claims;
  }

  public static Authentication none() {
    return new Builder().build();
  }

  public static Authentication of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public String authenticatedUsername() {
    return authenticatedUsername;
  }

  public String authenticatedClientId() {
    return authenticatedClientId;
  }

  public List<Long> authenticatedGroupKeys() {
    return authenticatedGroupKeys;
  }

  public List<String> authenticatedRoleIds() {
    return authenticatedRoleIds;
  }

  public List<String> authenticatedTenantIds() {
    return authenticatedTenantIds;
  }

  public List<String> authenticatedMappingIds() {
    return authenticatedMappingIds;
  }

  public Map<String, Object> claims() {
    return claims;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        authenticatedUsername,
        authenticatedGroupKeys,
        authenticatedRoleIds,
        authenticatedTenantIds,
        authenticatedMappingIds,
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
    return Objects.equals(authenticatedUsername, that.authenticatedUsername)
        && Objects.equals(authenticatedClientId, that.authenticatedClientId)
        && Objects.equals(authenticatedGroupKeys, that.authenticatedGroupKeys)
        && Objects.equals(authenticatedRoleIds, that.authenticatedRoleIds)
        && Objects.equals(authenticatedTenantIds, that.authenticatedTenantIds)
        && Objects.equals(authenticatedMappingIds, that.authenticatedMappingIds)
        && Objects.equals(claims, that.claims);
  }

  @Override
  public String toString() {
    return "Authentication["
        + "authenticatedUsername="
        + authenticatedUsername
        + ", "
        + "authenticatedClientId="
        + authenticatedClientId
        + ", "
        + "authenticatedGroupKeys="
        + authenticatedGroupKeys
        + ", "
        + "authenticatedRoleIds="
        + authenticatedRoleIds
        + ", "
        + "authenticatedTenantIds="
        + authenticatedTenantIds
        + ", "
        + "authenticatedMappingIds="
        + authenticatedMappingIds
        + ", "
        + "claims="
        + claims
        + ']';
  }

  public static final class Builder {

    private String username;
    private String clientId;
    private final List<Long> groupKeys = new ArrayList<>();
    private final List<String> roleIds = new ArrayList<>();
    private final List<String> tenants = new ArrayList<>();
    private final List<String> mappings = new ArrayList<>();
    private Map<String, Object> claims;

    public Builder user(final String value) {
      username = value;
      return this;
    }

    public Builder clientId(final String value) {
      clientId = value;
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

    public Builder role(final String value) {
      return roleIds(Collections.singletonList(value));
    }

    public Builder roleIds(final List<String> values) {
      if (values != null) {
        roleIds.addAll(values);
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

    public Builder mapping(final String mapping) {
      return mapping(Collections.singletonList(mapping));
    }

    public Builder mapping(final List<String> values) {
      if (values != null) {
        mappings.addAll(values);
      }
      return this;
    }

    public Builder claims(final Map<String, Object> value) {
      claims = value;
      return this;
    }

    public Authentication build() {
      return new Authentication(
          username,
          clientId,
          unmodifiableList(groupKeys),
          unmodifiableList(roleIds),
          unmodifiableList(tenants),
          unmodifiableList(mappings),
          claims);
    }
  }
}
