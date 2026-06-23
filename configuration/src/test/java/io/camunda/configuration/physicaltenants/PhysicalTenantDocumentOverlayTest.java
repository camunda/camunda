/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Document;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit tests for {@link PhysicalTenantDocumentConfigurations#forPhysicalTenant}: verifies the
 * two-phase overlay strategy that correctly merges per-tenant document overrides on top of the root
 * document catalog.
 */
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
    // given a root catalog with a single AWS store and no tenant-specific document config
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.shared-s3.bucket-name", "root-bucket",
                    "camunda.document.aws.shared-s3.bucket-path", "root/path",
                    "camunda.document.aws.shared-s3.region", "us-east-1")));

    // when resolving the document for a tenant that has no document overrides
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then the root store is inherited unchanged
    assertThat(doc.getAws()).containsKey("shared-s3");
    assertThat(doc.getAws().get("shared-s3").getBucketName()).isEqualTo("root-bucket");
    assertThat(doc.getAws().get("shared-s3").getBucketPath()).isEqualTo("root/path");
    assertThat(doc.getAws().get("shared-s3").getRegion()).isEqualTo("us-east-1");
  }

  @Test
  void shouldOverrideOnlyTheFieldTenantSets() {
    // given a root AWS store with bucket-name, bucket-path, and region, and a tenant that overrides
    // only bucket-path — the other fields must survive from the root
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

    // when resolving the document for tenanta
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then only the overridden field changes; root fields are preserved
    assertThat(doc.getAws().get("shared-s3").getBucketPath()).isEqualTo("tenant-a/path");
    assertThat(doc.getAws().get("shared-s3").getBucketName()).isEqualTo("root-bucket");
    assertThat(doc.getAws().get("shared-s3").getRegion()).isEqualTo("us-east-1");
  }

  @Test
  void shouldAddTenantPrivateStore() {
    // given a root catalog with one AWS store and a tenant declaring an additional private store
    // not present in the root
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

    // when resolving the document for tenanta
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then both the inherited root store and the new private store are present
    assertThat(doc.getAws()).containsKey("shared-s3");
    assertThat(doc.getAws()).containsKey("private-s3");
    assertThat(doc.getAws().get("private-s3").getBucketName()).isEqualTo("private-bucket");
  }

  @Test
  void shouldNarrowToAssignedWhenDeclared() {
    // given a root catalog with two AWS stores and a tenant declaring assigned: [shared-s3]
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

    // when resolving the document for tenanta
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then only the assigned store survives; the other root store is dropped
    assertThat(doc.getAws()).containsOnlyKeys("shared-s3");
  }

  @Test
  void shouldNotNarrowWhenAssignedIsEmpty() {
    // given a root catalog with two stores and a tenant declaring no assigned list
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.store-a.bucket-name", "bucket-a",
                    "camunda.document.aws.store-b.bucket-name", "bucket-b")));

    // when resolving the document for a tenant with no assigned declaration
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then the full catalog survives — an empty assigned list means no restriction
    assertThat(doc.getAws()).containsKeys("store-a", "store-b");
  }

  @Test
  void shouldClearAssignedFromRoot() {
    // given a root catalog that (hypothetically) has assigned set — this should never be
    // inherited by tenants; the overlay clears assigned before applying the tenant overlay
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.shared-s3.bucket-name", "root-bucket",
                    "camunda.document.assigned[0]", "shared-s3")));

    // when resolving the document for a tenant that declares no assigned of its own
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then the root's assigned is cleared — the tenant sees the full catalog, not the root's
    // restriction
    assertThat(doc.getAssigned()).isEmpty();
    assertThat(doc.getAws()).containsKey("shared-s3");
  }

  @Test
  void shouldResetDefaultStoreIdWhenDefaultDroppedByAssigned() {
    // given root catalog with two stores; default-store-id points to shared-s3, but tenanta's
    // assigned list only includes other-store — so shared-s3 (and the default) must be dropped
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

    // when
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then only other-store survives; shared-s3 is dropped along with the default-store-id
    assertThat(doc.getAws()).containsKey("other-store");
    assertThat(doc.getAws()).doesNotContainKey("shared-s3");
    assertThat(doc.getDefaultStoreId()).isNullOrEmpty();
  }
}
