/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class DefaultCamundaAuthentication implements CamundaAuthentication {

  @JsonProperty("authenticated_displayname")
  private String displayName;

  @JsonProperty("authenticated_email")
  private String email;

  @JsonProperty("authenticated_username")
  private String username;

  @JsonProperty("authenticated_client_id")
  private String clientId;

  @JsonProperty("authenticated_group_ids")
  private List<String> groupIds;

  @JsonProperty("authenticated_role_ids")
  private List<String> roleIds;

  @JsonProperty("authenticated_tenant_ids")
  private List<String> tenantIds;

  @JsonProperty("authenticated_mapping_ids")
  private List<String> mappingIds;

  @JsonProperty("claims")
  private Map<String, Object> claims;

  @Override
  public String getEmail() {
    return email;
  }

  @Override
  public CamundaAuthentication setEmail(final String email) {
    this.email = email;
    return this;
  }

  @Override
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public CamundaAuthentication setDisplayName(final String displayName) {
    this.displayName = displayName;
    return this;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public CamundaAuthentication setUsername(final String username) {
    this.username = username;
    return this;
  }

  @Override
  public String getClientId() {
    return clientId;
  }

  @Override
  public CamundaAuthentication setClientId(final String clientId) {
    this.clientId = clientId;
    return this;
  }

  @Override
  public List<String> getGroupIds() {
    return groupIds;
  }

  @Override
  public CamundaAuthentication setGroupIds(final List<String> groupIds) {
    this.groupIds = groupIds;
    return this;
  }

  @Override
  public List<String> getRoleIds() {
    return roleIds;
  }

  @Override
  public CamundaAuthentication setRoleIds(final List<String> roleIds) {
    this.roleIds = roleIds;
    return this;
  }

  @Override
  public List<String> getTenantIds() {
    return tenantIds;
  }

  @Override
  public CamundaAuthentication setTenantIds(final List<String> tenantIds) {
    this.tenantIds = tenantIds;
    return this;
  }

  @Override
  public List<String> getMappingIds() {
    return mappingIds;
  }

  @Override
  public CamundaAuthentication setMappingIds(final List<String> mappingIds) {
    this.mappingIds = mappingIds;
    return this;
  }

  @Override
  public Map<String, Object> getClaims() {
    return claims;
  }

  @Override
  public CamundaAuthentication setClaims(final Map<String, Object> claims) {
    this.claims = claims;
    return this;
  }
}
