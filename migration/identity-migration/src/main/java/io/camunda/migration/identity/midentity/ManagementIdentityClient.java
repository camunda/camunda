/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.midentity;

import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.dto.UserResourceAuthorization;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

public class ManagementIdentityClient {

  private static final String URL_PARAMS = "pageSize={1}";
  private static final String MIGRATION_MARK_STATUS_ENDPOINT = "/api/migration";
  private static final String MIGRATION_TENANTS_ENDPOINT = "/api/migration/tenant?" + URL_PARAMS;

  private final RestTemplate restTemplate;

  public ManagementIdentityClient(final RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  public List<UserResourceAuthorization> fetchUserResourceAuthorizations(
      final UserResourceAuthorization lastRecord, final int pageSize) {
    return new ArrayList<>();
  }

  public void markAuthorizationsAsMigrated(final Collection<UserResourceAuthorization> migrated) {}

  public List<Tenant> fetchTenants(final int pageSize) {
    return Arrays.stream(
            Objects.requireNonNull(
                restTemplate.getForObject(MIGRATION_TENANTS_ENDPOINT, Tenant[].class, pageSize)))
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
