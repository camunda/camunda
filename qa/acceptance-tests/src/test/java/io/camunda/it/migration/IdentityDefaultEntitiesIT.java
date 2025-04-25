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
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
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
      IdentityMigrationTestUtil.getManagementIdentity(POSTGRES, KEYCLOAK);

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  @TestZeebe(autoStart = false)
  final TestCamundaApplication camunda =
      new TestCamundaApplication()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true));

  @Test
  void canMigrateOldDefaultEntities() {

    // when -- Camunda is started with identity migration profile
    camunda
        .withAdditionalProfile(Profile.IDENTITY_MIGRATION)
        .withProperty("camunda.database.url", "http://" + ELASTICSEARCH.getHttpHostAddress())
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
