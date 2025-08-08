/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.client;

import io.camunda.identity.sdk.users.dto.User;
import io.camunda.migration.identity.dto.Authorization;
import io.camunda.migration.identity.dto.Client;
import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.dto.MappingRule;
import io.camunda.migration.identity.dto.Permission;
import io.camunda.migration.identity.dto.Role;
import io.camunda.migration.identity.dto.Tenant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.web.client.RestTemplate;

public class ManagementIdentityClient {

  private static final String MIGRATION_TENANTS_ENDPOINT = "/api/tenants";
  private static final String MIGRATION_USER_TENANTS_ENDPOINT = "/api/tenants/{0}/users";
  private static final String MIGRATION_GROUP_TENANTS_ENDPOINT = "/api/tenants/{0}/groups";
  private static final String MIGRATION_CLIENT_TENANTS_ENDPOINT = "/api/tenants/{0}/applications";
  private static final String MIGRATION_GROUPS_ENDPOINT = "/api/groups?page={0}&organizationId={1}";
  private static final String MIGRATION_GROUPS_AUTHORISATIONS_ENDPOINT =
      "/api/groups/{0}/authorizations";
  private static final String MIGRATION_USER_GROUPS_ENDPOINT =
      "/api/groups/{0}/users?organizationId={1}";
  private static final String MIGRATION_AUTHORIZATION_ENDPOINT =
      "/api/authorizations?organizationId={0}";
  private static final String MIGRATION_ROLES_ENDPOINT = "/api/roles";
  private static final String MIGRATION_ROLES_PERMISSIONS_ENDPOINT = "/api/roles/{0}/permissions";
  private static final String MIGRATION_USERS_ENDPOINT = "/api/users?page={0}";
  private static final String MIGRATION_USERS_ROLES_ENDPOINT = "/api/users/{0}/roles";
  private static final String MIGRATION_USERS_AUTHORIZATIONS_ENDPOINT =
      "/api/users/{0}/authorizations";
  private static final String MIGRATION_CLIENTS_ENDPOINT = "/api/clients";
  private static final String MIGRATION_CLIENTS_PERMISSIONS_ENDPOINT =
      "/api/clients/{0}/permissions";
  private static final String MIGRATION_MAPPING_RULES_ENDPOINT = "/api/mapping-rules";

  private final String organizationId;
  private final RestTemplate restTemplate;

  public ManagementIdentityClient(final RestTemplate restTemplate, final String organizationId) {
    this.restTemplate = restTemplate;
    this.organizationId = organizationId;
  }

  public List<Tenant> fetchTenants() {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(MIGRATION_TENANTS_ENDPOINT, Tenant[].class)))
        .toList();
  }

  public List<User> fetchTenantUsers(final String tenantId) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(MIGRATION_USER_TENANTS_ENDPOINT, User[].class, tenantId)))
        .toList();
  }

  public List<Group> fetchTenantGroups(final String tenantId) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(
                    MIGRATION_GROUP_TENANTS_ENDPOINT, Group[].class, tenantId)))
        .toList();
  }

  public List<Client> fetchTenantClients(final String tenantId) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(
                    MIGRATION_CLIENT_TENANTS_ENDPOINT, Client[].class, tenantId)))
        .toList();
  }

  public List<Group> fetchGroups(final int page) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(
                    MIGRATION_GROUPS_ENDPOINT, Group[].class, page, organizationId)))
        .toList();
  }

  public List<User> fetchGroupUsers(final String groupId) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(
                    MIGRATION_USER_GROUPS_ENDPOINT, User[].class, groupId, organizationId)))
        .toList();
  }

  public List<Authorization> fetchGroupAuthorizations(final String groupId) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(
                    MIGRATION_GROUPS_AUTHORISATIONS_ENDPOINT, Authorization[].class, groupId)))
        .toList();
  }

  public List<Authorization> fetchAuthorizations() {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(
                    MIGRATION_AUTHORIZATION_ENDPOINT, Authorization[].class, organizationId)))
        .toList();
  }

  public List<Role> fetchRoles() {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(MIGRATION_ROLES_ENDPOINT, Role[].class)))
        .toList();
  }

  public List<Permission> fetchPermissions(final String roleName) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(
                    MIGRATION_ROLES_PERMISSIONS_ENDPOINT, Permission[].class, roleName)))
        .toList();
  }

  public List<User> fetchUsers(final int page) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(MIGRATION_USERS_ENDPOINT, User[].class, page)))
        .toList();
  }

  public List<Role> fetchUserRoles(final String userId) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(MIGRATION_USERS_ROLES_ENDPOINT, Role[].class, userId)))
        .toList();
  }

  public List<Authorization> fetchUserAuthorizations(final String userId) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(
                    MIGRATION_USERS_AUTHORIZATIONS_ENDPOINT, Authorization[].class, userId)))
        .toList();
  }

  public List<Client> fetchClients() {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(MIGRATION_CLIENTS_ENDPOINT, Client[].class)))
        .toList();
  }

  public List<Permission> fetchClientPermissions(final String clientId) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(
                    MIGRATION_CLIENTS_PERMISSIONS_ENDPOINT, Permission[].class, clientId)))
        .toList();
  }

  public List<MappingRule> fetchMappingRules() {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(MIGRATION_MAPPING_RULES_ENDPOINT, MappingRule[].class)))
        .toList();
  }
}
