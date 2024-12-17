/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static io.camunda.it.migration.IdentityMigrationTestUtil.externalIdentityUrl;
import static io.camunda.it.migration.IdentityMigrationTestUtil.getIdentityAccessToken;

import io.camunda.application.Profile;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ZeebeIntegration
@Testcontainers(parallel = true)
public class IdentityDefaultEntitiesIT {
  @Container
  private static final GenericContainer<?> POSTGRES = IdentityMigrationTestUtil.getPostgres();

  @Container
  private static final GenericContainer<?> KEYCLOAK =
      IdentityMigrationTestUtil.getKeycloak(POSTGRES);

  @Container
  private static final GenericContainer<?> IDENTITY =
      IdentityMigrationTestUtil.getManagementIdentity(POSTGRES, KEYCLOAK);

  @TestZeebe(autoStart = false)
  final TestStandaloneCamunda camunda = new TestStandaloneCamunda();

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
            "camunda.migration.identity.managementIdentity.m2mToken",
            getIdentityAccessToken(KEYCLOAK))
        .start();

    // then -- the migration did not fail
  }
}
