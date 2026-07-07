/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import com.azure.storage.blob.BlobServiceClientBuilder;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.AzuriteContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
final class DocumentIsolationAzureIT extends AbstractDocumentIsolationIT {

  private static final String CONTAINER_A = "container-a";
  private static final String CONTAINER_B = "container-b";

  @Container private static final AzuriteContainer AZURITE = new AzuriteContainer();

  @SuppressWarnings("resource") // lifecycle managed by @TestZeebe
  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @BeforeAll
  static void setUp() {
    final var azureClient =
        new BlobServiceClientBuilder()
            .connectionString(AZURITE.externalConnectionString())
            .buildClient();
    azureClient.createBlobContainer(CONTAINER_A);
    azureClient.createBlobContainer(CONTAINER_B);

    BROKER
        .withProperty("camunda.document.azure.store-default.container-name", CONTAINER_B)
        .withProperty("camunda.document.azure.store-default.container-path", "default/")
        .withProperty(
            "camunda.document.azure.store-default.connection-string",
            AZURITE.externalConnectionString())
        .withProperty("camunda.document.default-store-id", STORE_DEFAULT)
        .withProperty(
            "camunda.physical-tenants.tenanta.document.azure.store-a.container-name", CONTAINER_A)
        .withProperty(
            "camunda.physical-tenants.tenanta.document.azure.store-a.connection-string",
            AZURITE.externalConnectionString())
        .withProperty("camunda.physical-tenants.tenanta.document.default-store-id", STORE_A)
        .withProperty("camunda.physical-tenants.tenanta.document.assigned[0]", STORE_A)
        .withProperty(
            "camunda.physical-tenants.tenantb.document.azure.store-b.container-name", CONTAINER_B)
        .withProperty(
            "camunda.physical-tenants.tenantb.document.azure.store-b.connection-string",
            AZURITE.externalConnectionString())
        .withProperty("camunda.physical-tenants.tenantb.document.default-store-id", STORE_B)
        .withProperty("camunda.physical-tenants.tenantb.document.assigned[0]", STORE_B)
        .withProperty(
            "camunda.physical-tenants.tenantc.document.azure.store-c.container-name", CONTAINER_A)
        .withProperty(
            "camunda.physical-tenants.tenantc.document.azure.store-c.container-path", "tenantc/")
        .withProperty(
            "camunda.physical-tenants.tenantc.document.azure.store-c.connection-string",
            AZURITE.externalConnectionString())
        .withProperty("camunda.physical-tenants.tenantc.document.default-store-id", STORE_C)
        .withProperty("camunda.physical-tenants.tenantc.document.assigned[0]", STORE_C)
        .start();

    startClients(BROKER);
  }

  @AfterAll
  static void tearDown() {
    closeClients();
  }
}
