/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.midentity;

import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.dto.MappingRule.MappingRuleType;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Role;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.dto.TenantMappingRule;
import io.camunda.migration.identity.dto.UserGroups;
import io.camunda.migration.identity.dto.UserResourceAuthorization;
import io.camunda.migration.identity.dto.UserTenants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

public class ManagementIdentityClient {

  private static final String URL_PARAMS = "pageSize={0}";
  private static final String MIGRATION_MARK_STATUS_ENDPOINT = "/api/migration";
  private static final String MIGRATION_TENANTS_ENDPOINT = "/api/migration/tenant?" + URL_PARAMS;
  private static final String MIGRATION_ROLES_ENDPOINT = "/api/migration/role?" + URL_PARAMS;
  private static final String MIGRATION_USER_TENANTS_ENDPOINT =
      "/api/migration/tenant/user?" + URL_PARAMS;
  private static final String MIGRATION_MAPPING_RULE_ENDPOINT =
      "/api/migration/mapping-rule?" + URL_PARAMS + "&type={1}";
  private static final String MIGRATION_GROUPS_ENDPOINT =
      "/api/migration/group?" + URL_PARAMS + "&organizationId={1}";
  private static final String MIGRATION_USER_GROUP_ENDPOINT =
      "/api/migration/group/user?" + URL_PARAMS;

  private final String organizationId;
  private final RestTemplate restTemplate;

  public ManagementIdentityClient(final RestTemplate restTemplate, final String organizationId) {
    this.restTemplate = restTemplate;
    this.organizationId = organizationId;
  }

  public List<UserResourceAuthorization> fetchUserResourceAuthorizations(final int pageSize) {
    return new ArrayList<>();
  }

  public void markAuthorizationsAsMigrated(final Collection<UserResourceAuthorization> migrated) {}

  public List<TenantMappingRule> fetchTenantMappingRules(final int pageSize) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(
                    MIGRATION_MAPPING_RULE_ENDPOINT,
                    TenantMappingRule[].class,
                    pageSize,
                    MappingRuleType.TENANT)))
        .toList();
  }

  public List<UserTenants> fetchUserTenants(final int pageSize) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(
                    MIGRATION_USER_TENANTS_ENDPOINT, UserTenants[].class, pageSize)))
        .toList();
  }

  public List<Tenant> fetchTenants(final int pageSize) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(MIGRATION_TENANTS_ENDPOINT, Tenant[].class, pageSize)))
        .toList();
  }

  public List<Group> fetchGroups(final int pageSize) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(
                    MIGRATION_GROUPS_ENDPOINT, Group[].class, pageSize, organizationId)))
        .toList();
  }

  public List<Role> fetchRoles(final int pageSize) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(MIGRATION_ROLES_ENDPOINT, Role[].class, pageSize)))
        .toList();
  }

  public List<UserGroups> fetchUserGroups(final int pageSize) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(
                    MIGRATION_USER_GROUP_ENDPOINT, UserGroups[].class, pageSize)))
        .toList();
  }

  public void updateMigrationStatus(final Collection<MigrationStatusUpdateRequest> migrations) {
    if (migrations != null && !migrations.isEmpty()) {
      final HttpEntity<Collection<MigrationStatusUpdateRequest>> requestEntity =
          new HttpEntity<>(migrations, null);
      restTemplate.exchange(
          MIGRATION_MARK_STATUS_ENDPOINT, HttpMethod.POST, requestEntity, Void.class);
    }
  }
}
