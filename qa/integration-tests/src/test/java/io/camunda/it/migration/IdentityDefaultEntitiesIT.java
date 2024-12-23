/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static io.camunda.it.migration.IdentityMigrationTestUtil.CAMUNDA_IDENTITY_RESOURCE_SERVER;
import static io.camunda.it.migration.IdentityMigrationTestUtil.IDENTITY_CLIENT;
import static io.camunda.it.migration.IdentityMigrationTestUtil.IDENTITY_CLIENT_SECRET;
import static io.camunda.it.migration.IdentityMigrationTestUtil.externalIdentityUrl;

import io.camunda.application.Profile;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ZeebeIntegration
@Testcontainers(parallel = true)
@Disabled
public class IdentityDefaultEntitiesIT {

  @Container
  private static final GenericContainer<?> POSTGRES = IdentityMigrationTestUtil.getPostgres();

  @Container
  private static final GenericContainer<?> KEYCLOAK =
      IdentityMigrationTestUtil.getKeycloak(POSTGRES);

  @Container
  private static final GenericContainer<?> IDENTITY =
      IdentityMigrationTestUtil.getManagementIdentity(POSTGRES, KEYCLOAK)
          .withEnv("KEYCLOAK_GROUPS_0_NAME", "group0")
          .withEnv("KEYCLOAK_GROUPS_1_NAME", "group1")
          .withEnv("KEYCLOAK_GROUPS_2_NAME", "group2")
          .withEnv("IDENTITY_TENANTS_0_NAME", "tenant0")
          .withEnv("IDENTITY_TENANTS_0_TENANTID", "tenant0")
          .withEnv("IDENTITY_TENANTS_0_MEMBERS_0_TYPE", "GROUP")
          .withEnv("IDENTITY_TENANTS_0_MEMBERS_0_GROUPNAME", "group0")
          .withEnv("IDENTITY_TENANTS_0_MEMBERS_1_TYPE", "GROUP")
          .withEnv("IDENTITY_TENANTS_0_MEMBERS_1_GROUPNAME", "group1")
          .withEnv("IDENTITY_TENANTS_1_NAME", "tenant1")
          .withEnv("IDENTITY_TENANTS_1_TENANTID", "tenant1")
          .withEnv("IDENTITY_TENANTS_1_MEMBERS_0_TYPE", "GROUP")
          .withEnv("IDENTITY_TENANTS_1_MEMBERS_0_GROUPNAME", "group1")
          .withEnv("IDENTITY_TENANTS_1_MEMBERS_1_TYPE", "GROUP")
          .withEnv("IDENTITY_TENANTS_1_MEMBERS_1_GROUPNAME", "group2");

  @TestZeebe(autoStart = false)
  final TestStandaloneCamunda camunda =
      new TestStandaloneCamunda()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true));

  @Test
  void canMigrateOldDefaultEntities() {
    // given -- default entities in the old identity service

    // when -- Camunda is started with identity migration profile
    camunda
        .withAdditionalProfile(Profile.IDENTITY_MIGRATION)
        .withProperty("camunda.migration.identity.zeebe.gatewayAddress", camunda.restAddress())
        .withProperty(
            "camunda.migration.identity.managementIdentity.baseUrl", externalIdentityUrl(IDENTITY))
        .withProperty(
            "camunda.migration.identity.management-identity.issuer-backend-url",
            IdentityMigrationTestUtil.externalKeycloakUrl(KEYCLOAK) + "/realms/camunda-platform/")
        .withProperty("camunda.migration.identity.management-identity.issuer-type", "KEYCLOAK")
        .withProperty("camunda.migration.identity.management-identity.client-id", IDENTITY_CLIENT)
        .withProperty(
            "camunda.migration.identity.management-identity.client-secret", IDENTITY_CLIENT_SECRET)
        .withProperty(
            "camunda.migration.identity.management-identity.audience",
            CAMUNDA_IDENTITY_RESOURCE_SERVER)
        .start();

    // then -- the migration did not fail
  }
}
