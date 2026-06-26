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
  void shouldNotNarrowCatalogWhenRootAssignedIsSet() {
    // narrowing reads only from the tenant prefix, so root assigned has no effect
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.document.aws.shared-s3.bucket-name", "root-bucket",
                    "camunda.document.assigned[0]", "shared-s3")));

    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    assertThat(doc.getAws()).containsKey("shared-s3");
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
}
