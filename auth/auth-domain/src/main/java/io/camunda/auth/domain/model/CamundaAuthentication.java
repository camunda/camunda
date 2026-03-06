/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.model;

import static java.util.Collections.unmodifiableList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Represents the authentication context for a user or client in Camunda, including their username or
 * client ID, group memberships, roles, tenants, mapping rules, and associated claims.
 *
 * <p>Either {@code authenticatedUsername} or {@code authenticatedClientId} must be set, but not
 * both, unless the authentication represents an anonymous user in which case both can be null.
 */
public record CamundaAuthentication(
    String authenticatedUsername,
    String authenticatedClientId,
    boolean anonymousUser,
    List<String> authenticatedGroupIds,
    List<String> authenticatedRoleIds,
    List<String> authenticatedTenantIds,
    List<String> authenticatedMappingRuleIds,
    Map<String, Object> claims)
    implements Serializable {

  public CamundaAuthentication {
    if (!anonymousUser && (authenticatedUsername != null && authenticatedClientId != null)) {
      throw new IllegalArgumentException("Either username or clientId must be set, not both.");
    }
  }

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
