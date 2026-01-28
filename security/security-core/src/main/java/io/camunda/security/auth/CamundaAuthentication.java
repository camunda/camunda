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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Represents the authentication context for a user or client in Camunda, including (where
 * appropriate) their username or client ID, group memberships, roles, tenants, mapping rules, and
 * associated claims.
 *
 * <p>Either {@code authenticatedUsername} or {@code authenticatedClientId} must be set, but not
 * both, unless the authentication represents an anonymous user {@code anonymousUser} in which case
 * both can be null.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record CamundaAuthentication(
    @JsonProperty("authenticated_username") String authenticatedUsername,
    @JsonProperty("authenticated_client_id") String authenticatedClientId,
    @JsonProperty("anonymous_user") boolean anonymousUser,
    @JsonProperty("authenticated_group_ids") List<String> authenticatedGroupIds,
    @JsonProperty("authenticated_role_ids") List<String> authenticatedRoleIds,
    @JsonProperty("authenticated_tenant_ids") List<String> authenticatedTenantIds,
    @JsonProperty("authenticated_mapping_rule_ids") List<String> authenticatedMappingRuleIds,
    @JsonProperty("claims") Map<String, Object> claims)
    implements Serializable {

  public CamundaAuthentication {
    if (!anonymousUser && (authenticatedUsername != null && authenticatedClientId != null)) {
      throw new IllegalArgumentException("Either username or clientId must be set, not both.");
    }
  }

  @JsonIgnore
  public boolean isAnonymous() {
    return anonymousUser;
  }

  public static CamundaAuthentication none() {
    return of(b -> b);
  }

  public static CamundaAuthentication anonymous() {
    return of(b -> b.anonymous(true));
  }

  public static CamundaAuthentication of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public static final class Builder {

    private String username;
    private String clientId;
    private boolean anonymous;
    private final List<String> groupIds = new ArrayList<>();
    private final List<String> roleIds = new ArrayList<>();
    private final List<String> tenants = new ArrayList<>();
    private final List<String> mappingRules = new ArrayList<>();
    private Map<String, Object> claims;

    public Builder user(final String value) {
      username = value;
      return this;
    }

    public Builder clientId(final String value) {
      clientId = value;
      return this;
    }

    public Builder anonymous(final boolean value) {
      anonymous = value;
      return this;
    }

    public Builder group(final String value) {
      return groupIds(Collections.singletonList(value));
    }

    public Builder groupIds(final List<String> values) {
      if (values != null) {
        groupIds.addAll(values);
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

    public Builder mappingRule(final String mappingRule) {
      return mappingRule(Collections.singletonList(mappingRule));
    }

    public Builder mappingRule(final List<String> values) {
      if (values != null) {
        mappingRules.addAll(values);
      }
      return this;
    }

    public Builder claims(final Map<String, Object> value) {
      claims = value;
      return this;
    }

    public CamundaAuthentication build() {
      return new CamundaAuthentication(
          username,
          clientId,
          anonymous,
          unmodifiableList(groupIds),
          unmodifiableList(roleIds),
          unmodifiableList(tenants),
          unmodifiableList(mappingRules),
          claims);
    }
  }
}
