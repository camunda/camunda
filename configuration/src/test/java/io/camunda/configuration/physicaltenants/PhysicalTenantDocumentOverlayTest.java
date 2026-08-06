/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.configuration.Document;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantDocumentOverlayTest {

  private MockEnvironment environment;

  @BeforeEach
  void setUp() {
    environment = new MockEnvironment();
    UnifiedConfigurationHelper.setCustomEnvironment(environment);
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldInheritRootStoreWhenTenantHasNoOverride() {
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.shared-s3.bucket-name", "root-bucket",
                    "camunda.document.aws.shared-s3.bucket-path", "root/path",
                    "camunda.document.aws.shared-s3.region", "us-east-1")));

    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    assertThat(doc.getAws()).containsKey("shared-s3");
    assertThat(doc.getAws().get("shared-s3").getBucketName()).isEqualTo("root-bucket");
    assertThat(doc.getAws().get("shared-s3").getBucketPath()).isEqualTo("root/path");
    assertThat(doc.getAws().get("shared-s3").getRegion()).isEqualTo("us-east-1");
  }

  @Test
  void shouldOverrideOnlyTheFieldTenantSets() {
    // tenant overrides only bucket-path; root's bucket-name and region must survive
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.shared-s3.bucket-name", "root-bucket",
                    "camunda.document.aws.shared-s3.bucket-path", "root/path",
                    "camunda.document.aws.shared-s3.region", "us-east-1",
                    "camunda.physical-tenants.tenanta.document.aws.shared-s3.bucket-path",
                        "tenant-a/path")));

    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    assertThat(doc.getAws().get("shared-s3").getBucketPath()).isEqualTo("tenant-a/path");
    assertThat(doc.getAws().get("shared-s3").getBucketName()).isEqualTo("root-bucket");
    assertThat(doc.getAws().get("shared-s3").getRegion()).isEqualTo("us-east-1");
  }

  @Test
  void shouldAddTenantPrivateStore() {
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.shared-s3.bucket-name", "root-bucket",
                    "camunda.document.aws.shared-s3.bucket-path", "root/path",
                    "camunda.physical-tenants.tenanta.document.aws.private-s3.bucket-name",
                        "private-bucket",
                    "camunda.physical-tenants.tenanta.document.aws.private-s3.bucket-path",
                        "tenanta/private")));

    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    assertThat(doc.getAws()).containsKey("shared-s3");
    assertThat(doc.getAws()).containsKey("private-s3");
    assertThat(doc.getAws().get("private-s3").getBucketName()).isEqualTo("private-bucket");
  }

  @Test
  void shouldNarrowToAssignedWhenDeclared() {
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.shared-s3.bucket-name", "root-bucket",
                    "camunda.document.aws.shared-s3.bucket-path", "root/path",
                    "camunda.document.aws.other-store.bucket-name", "other-bucket",
                    "camunda.document.aws.other-store.bucket-path", "other/path",
                    "camunda.physical-tenants.tenanta.document.assigned[0]", "shared-s3")));

    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    assertThat(doc.getAws()).containsOnlyKeys("shared-s3");
  }

  @Test
  void shouldNotNarrowWhenAssignedIsEmpty() {
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.store-a.bucket-name", "bucket-a",
                    "camunda.document.aws.store-b.bucket-name", "bucket-b")));

    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    assertThat(doc.getAws()).containsKeys("store-a", "store-b");
  }

  @Test
  void shouldRejectRootAssignedEvenThoughTenantOverlayWouldIgnoreIt() {
    // narrowing reads only from the tenant prefix, so root assigned has no effect
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.shared-s3.bucket-name", "root-bucket",
                    "camunda.document.assigned[0]", "shared-s3")));

    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(
            () -> PhysicalTenantDocumentAssignedValidation.validateRootAssignedAbsent(environment))
        .withMessageContaining("camunda.document.assigned");
  }

  @Test
  void shouldFailWhenDefaultStoreIdExcludedByAssigned() {
    // default-store-id points to shared-s3 but assigned only includes other-store —
    // misconfiguration
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.default-store-id", "shared-s3",
                    "camunda.document.aws.shared-s3.bucket-name", "global-docs",
                    "camunda.document.aws.other-store.bucket-name", "other-docs",
                    "camunda.physical-tenants.tenanta.document.assigned[0]", "other-store")));

    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(
            () -> PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("shared-s3")
        .withMessageContaining("assigned");
  }

  @Test
  void shouldKeepDefaultStoreIdWhenIncludedInAssigned() {
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.default-store-id", "shared-s3",
                    "camunda.document.aws.shared-s3.bucket-name", "global-docs",
                    "camunda.document.aws.other-store.bucket-name", "other-docs",
                    "camunda.physical-tenants.tenanta.document.assigned[0]", "shared-s3")));

    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    assertThat(doc.getAws()).containsOnlyKeys("shared-s3");
    assertThat(doc.getDefaultStoreId()).isEqualTo("shared-s3");
  }

  @Test
  void shouldFailWhenSameStoreIdUsedAcrossProviders() {
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.abc.bucket-name", "aws-bucket",
                    "camunda.document.gcp.abc.bucket-name", "gcp-bucket")));

    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(
            () -> PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment))
        .withMessageContaining("abc");
  }

  @Test
  void shouldFailWhenTenantPrivateStoreIdCollidesWithRootStoreId() {
    // tenant introduces a private gcp store with the same id as a root aws store
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.abc.bucket-name", "aws-bucket",
                    "camunda.physical-tenants.tenanta.document.gcp.abc.bucket-name",
                        "gcp-bucket")));

    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(
            () -> PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment))
        .withMessageContaining("abc");
  }

  @Test
  void shouldFailWhenRootAssignedIsSet() {
    environment.setProperty("camunda.document.assigned[0]", "some-store");

    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(
            () -> PhysicalTenantDocumentAssignedValidation.validateRootAssignedAbsent(environment))
        .withMessageContaining("camunda.document.assigned")
        .withMessageContaining("physical-tenants.<id>.document.assigned");
  }

  @Test
  void shouldPassWhenRootAssignedIsAbsent() {
    assertThatCode(
            () -> PhysicalTenantDocumentAssignedValidation.validateRootAssignedAbsent(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldResolveCredentialsPerPhysicalTenant() {
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.store.bucket-name", "root-bucket",
                    "camunda.document.aws.store.access-key", "root-key",
                    "camunda.document.aws.store.secret-key", "root-secret",
                    "camunda.document.gcp.gcs.bucket-name", "root-gcs",
                    "camunda.document.gcp.gcs.credentials-path", "/secrets/root.json",
                    "camunda.physical-tenants.tenanta.document.aws.store.access-key",
                        "tenant-a-key",
                    "camunda.physical-tenants.tenanta.document.aws.store.secret-key",
                        "tenant-a-secret",
                    "camunda.physical-tenants.tenanta.document.gcp.gcs.credentials-path",
                        "/secrets/tenant-a.json")));

    final Document tenantA =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);
    final Document tenantB =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenantb", environment);

    assertThat(tenantA.getAws().get("store").getAccessKey()).isEqualTo("tenant-a-key");
    assertThat(tenantA.getAws().get("store").getSecretKey()).isEqualTo("tenant-a-secret");
    assertThat(tenantA.getGcp().get("gcs").getCredentialsPath())
        .isEqualTo("/secrets/tenant-a.json");
    // the tenant restated only its credentials, so the root bucket must survive the overlay
    assertThat(tenantA.getAws().get("store").getBucketName()).isEqualTo("root-bucket");

    // a tenant without an override keeps the root credentials
    assertThat(tenantB.getAws().get("store").getAccessKey()).isEqualTo("root-key");
    assertThat(tenantB.getAws().get("store").getSecretKey()).isEqualTo("root-secret");
    assertThat(tenantB.getGcp().get("gcs").getCredentialsPath()).isEqualTo("/secrets/root.json");
  }

  @Test
  void shouldRejectATenantOverridingOnlyOneHalfOfTheKeyPair() {
    // the unrestated half is inherited from the root, so the store would be built with an access
    // key and a secret key belonging to two different identities — a pairing that passes the
    // store's both-or-neither check and only fails at the first document operation
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.store.bucket-name", "root-bucket",
                    "camunda.document.aws.store.access-key", "root-key",
                    "camunda.document.aws.store.secret-key", "root-secret",
                    "camunda.physical-tenants.tenanta.document.aws.store.access-key",
                        "tenant-a-key")));

    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(
            () -> PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("store")
        .withMessageContaining("access-key")
        .withMessageContaining("secret-key");
  }

  @Test
  void shouldAcceptATenantOverridingNeitherHalfOfTheKeyPair() {
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.store.bucket-name", "root-bucket",
                    "camunda.document.aws.store.access-key", "root-key",
                    "camunda.document.aws.store.secret-key", "root-secret",
                    "camunda.physical-tenants.tenanta.document.aws.store.bucket-path",
                        "tenant-a")));

    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    assertThat(doc.getAws().get("store").getAccessKey()).isEqualTo("root-key");
    assertThat(doc.getAws().get("store").getSecretKey()).isEqualTo("root-secret");
  }

  @Test
  void shouldResolveAzureConnectionStringPerPhysicalTenant() {
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.azure.blobs.container-name", "root-container",
                    "camunda.document.azure.blobs.container-path", "root/path",
                    "camunda.document.azure.blobs.connection-string",
                        "AccountName=root;AccountKey=root-key",
                    "camunda.physical-tenants.tenanta.document.azure.blobs.connection-string",
                        "AccountName=tenanta;AccountKey=tenant-a-key")));

    final Document tenantA =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);
    final Document tenantB =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenantb", environment);

    assertThat(tenantA.getAzure().get("blobs").getConnectionString())
        .isEqualTo("AccountName=tenanta;AccountKey=tenant-a-key");
    // the tenant restated only its connection string, so the root container must survive the
    // overlay — losing it would silently send the tenant's documents to the default container
    assertThat(tenantA.getAzure().get("blobs").getContainerName()).isEqualTo("root-container");
    assertThat(tenantA.getAzure().get("blobs").getContainerPath()).isEqualTo("root/path");

    // a tenant without an override keeps the root connection string
    assertThat(tenantB.getAzure().get("blobs").getConnectionString())
        .isEqualTo("AccountName=root;AccountKey=root-key");
  }

  @Test
  void shouldGiveEachPhysicalTenantItsOwnAzureStore() {
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.physical-tenants.tenanta.document.azure.blobs-a.container-name",
                        "container-a",
                    "camunda.physical-tenants.tenanta.document.azure.blobs-a.connection-string",
                        "AccountName=tenanta;AccountKey=tenant-a-key",
                    "camunda.physical-tenants.tenantb.document.azure.blobs-b.container-name",
                        "container-b",
                    "camunda.physical-tenants.tenantb.document.azure.blobs-b.connection-string",
                        "AccountName=tenantb;AccountKey=tenant-b-key")));

    final Document tenantA =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);
    final Document tenantB =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenantb", environment);

    // neither tenant may see the other's store, let alone its credentials
    assertThat(tenantA.getAzure()).containsOnlyKeys("blobs-a");
    assertThat(tenantB.getAzure()).containsOnlyKeys("blobs-b");
    assertThat(tenantA.getAzure().get("blobs-a").getConnectionString())
        .isEqualTo("AccountName=tenanta;AccountKey=tenant-a-key");
    assertThat(tenantB.getAzure().get("blobs-b").getConnectionString())
        .isEqualTo("AccountName=tenantb;AccountKey=tenant-b-key");
  }
}
