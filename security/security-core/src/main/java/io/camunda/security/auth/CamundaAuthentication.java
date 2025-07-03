/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import io.camunda.zeebe.auth.Authorization;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface CamundaAuthentication {

  CamundaAuthentication NONE_AUTHENTICATION = of(b -> b);
  CamundaAuthentication ANONYMOUS_AUTHENTICATION =
      new CamundaAuthentication.Builder()
          .claims(Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, true))
          .build();

  String getEmail();

  CamundaAuthentication setEmail(String email);

  String getDisplayName();

  CamundaAuthentication setDisplayName(String displayName);

  String getUsername();

  CamundaAuthentication setUsername(String username);

  String getClientId();

  CamundaAuthentication setClientId(String clientId);

  List<String> getGroupIds();

  CamundaAuthentication setGroupIds(List<String> groups);

  List<String> getRoleIds();

  CamundaAuthentication setRoleIds(List<String> roles);

  List<String> getTenantIds();

  CamundaAuthentication setTenantIds(List<String> tenants);

  List<String> getMappingIds();

  CamundaAuthentication setMappingIds(List<String> mappings);

  Map<String, Object> getClaims();

  CamundaAuthentication setClaims(final Map<String, Object> claims);

  static CamundaAuthentication none() {
    return NONE_AUTHENTICATION;
  }

  static CamundaAuthentication anonymous() {
    return ANONYMOUS_AUTHENTICATION;
  }

  static CamundaAuthentication of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  final class Builder {

    private String email;
    private String displayName;
    private String username;
    private String clientId;
    private List<String> groupIds;
    private List<String> roleIds;
    private List<String> tenants;
    private List<String> mappings;
    private Map<String, Object> claims;

    public Builder email(final String value) {
      email = value;
      return this;
    }

    public Builder displayName(final String value) {
      displayName = value;
      return this;
    }

    public Builder username(final String value) {
      username = value;
      return this;
    }

    public Builder clientId(final String value) {
      clientId = value;
      return this;
    }

    public Builder groupId(final String value) {
      return groupIds(Collections.singletonList(value));
    }

    public Builder groupIds(final List<String> values) {
      if (values != null) {
        groupIds.addAll(values);
      }
      return this;
    }

    public Builder roleId(final String value) {
      return roleIds(Collections.singletonList(value));
    }

    public Builder roleIds(final List<String> values) {
      if (values != null) {
        roleIds.addAll(values);
      }
      return this;
    }

    public Builder tenantId(final String tenant) {
      return tenantIds(Collections.singletonList(tenant));
    }

    public Builder tenantIds(final List<String> values) {
      if (values != null) {
        tenants.addAll(values);
      }
      return this;
    }

    public Builder mappingId(final String mapping) {
      return mappingIds(Collections.singletonList(mapping));
    }

    public Builder mappingIds(final List<String> values) {
      if (values != null) {
        mappings.addAll(values);
      }
      return this;
    }

    public Builder claims(final Map<String, Object> value) {
      claims = value;
      return this;
    }

    public CamundaAuthentication build() {
      return new DefaultCamundaAuthentication()
          .setUsername(username)
          .setEmail(email)
          .setDisplayName(displayName)
          .setClientId(clientId)
          .setGroupIds(groupIds)
          .setRoleIds(roleIds)
          .setTenantIds(tenants)
          .setMappingIds(mappings)
          .setClaims(claims);
    }
  }
}
