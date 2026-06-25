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
    // given
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.shared-s3.bucket-name", "root-bucket",
                    "camunda.document.aws.shared-s3.bucket-path", "root/path",
                    "camunda.document.aws.shared-s3.region", "us-east-1")));

    // when
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then
    assertThat(doc.getAws()).containsKey("shared-s3");
    assertThat(doc.getAws().get("shared-s3").getBucketName()).isEqualTo("root-bucket");
    assertThat(doc.getAws().get("shared-s3").getBucketPath()).isEqualTo("root/path");
    assertThat(doc.getAws().get("shared-s3").getRegion()).isEqualTo("us-east-1");
  }

  @Test
  void shouldOverrideOnlyTheFieldTenantSets() {
    // given tenant overrides only bucket-path; other root fields must survive
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

    // when
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then
    assertThat(doc.getAws().get("shared-s3").getBucketPath()).isEqualTo("tenant-a/path");
    assertThat(doc.getAws().get("shared-s3").getBucketName()).isEqualTo("root-bucket");
    assertThat(doc.getAws().get("shared-s3").getRegion()).isEqualTo("us-east-1");
  }

  @Test
  void shouldAddTenantPrivateStore() {
    // given
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

    // when
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then both the inherited root store and the new private store are present
    assertThat(doc.getAws()).containsKey("shared-s3");
    assertThat(doc.getAws()).containsKey("private-s3");
    assertThat(doc.getAws().get("private-s3").getBucketName()).isEqualTo("private-bucket");
  }

  @Test
  void shouldNarrowToAssignedWhenDeclared() {
    // given
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

    // when
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then only the assigned store survives
    assertThat(doc.getAws()).containsOnlyKeys("shared-s3");
  }

  @Test
  void shouldNotNarrowWhenAssignedIsEmpty() {
    // given
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.store-a.bucket-name", "bucket-a",
                    "camunda.document.aws.store-b.bucket-name", "bucket-b")));

    // when
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then full catalog survives — empty assigned means no restriction
    assertThat(doc.getAws()).containsKeys("store-a", "store-b");
  }

  @Test
  void shouldClearAssignedFromRoot() {
    // given root has assigned set — must never be inherited by tenants
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.shared-s3.bucket-name", "root-bucket",
                    "camunda.document.assigned[0]", "shared-s3")));

    // when
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then
    assertThat(doc.getAssigned()).isEmpty();
    assertThat(doc.getAws()).containsKey("shared-s3");
  }

  @Test
  void shouldResetDefaultStoreIdWhenDefaultDroppedByAssigned() {
    // given default-store-id points to shared-s3, but tenant's assigned only includes other-store
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

    // then shared-s3 is dropped along with the default-store-id
    assertThat(doc.getAws()).containsKey("other-store");
    assertThat(doc.getAws()).doesNotContainKey("shared-s3");
    assertThat(doc.getDefaultStoreId()).isNullOrEmpty();
  }
}
