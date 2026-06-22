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
import io.camunda.configuration.Document.AwsStore;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * SPIKE (POC) — proves the per-tenant document-store overlayimplemented by {@link
 * PhysicalTenantDocumentConfigurations}. Counterpart to {@code
 * ExporterArgsOverlayCharacterizationTest}, which pins the native (broken) behavior: here we prove
 * the overlay repairs it.
 *
 * <p>Each test seeds a root catalog plus a {@code tenanta} overlay and resolves only the document
 * config (the overlay in isolation — no per-PT required-override validation, which is exercised
 * end-to-end in {@link PhysicalTenantResolverDocumentTest}).
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

  private void withProperties(final Map<String, Object> properties) {
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
  }

  @Test
  void shouldFieldOverrideSharedStoreInsteadOfReplacing() {
    // given a root store with three fields and a tenant overriding ONLY the bucket-path
    final Map<String, Object> properties = new HashMap<>();
    properties.put("camunda.document.aws.shared-s3.bucket-name", "global-docs");
    properties.put("camunda.document.aws.shared-s3.region", "eu-west-1");
    properties.put("camunda.document.aws.shared-s3.bucket-path", "root/");
    properties.put(
        "camunda.physical-tenants.tenanta.document.aws.shared-s3.bucket-path", "tenant-a/");
    withProperties(properties);

    // when the tenant's document config is resolved
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then the overridden field wins and the omitted fields are INHERITED (not nulled — the footgun
    // the native overlay would hit, see ExporterArgsOverlayCharacterizationTest)
    final AwsStore store = doc.getAws().get("shared-s3");
    assertThat(store).as("the shared store survives the overlay").isNotNull();
    assertThat(store.getBucketPath())
        .as("overridden field takes the tenant value")
        .isEqualTo("tenant-a/");
    assertThat(store.getBucketName()).as("omitted field inherits root").isEqualTo("global-docs");
    assertThat(store.getRegion()).as("omitted field inherits root").isEqualTo("eu-west-1");
  }

  @Test
  void shouldInheritStoreNotTouchedByTenant() {
    // given a root catalog with two stores and a tenant touching only one
    final Map<String, Object> properties = new HashMap<>();
    properties.put("camunda.document.aws.shared-s3.bucket-name", "global-docs");
    properties.put("camunda.document.gcp.analytics-gcs.bucket-name", "analytics");
    properties.put("camunda.document.gcp.analytics-gcs.prefix", "root/");
    properties.put(
        "camunda.physical-tenants.tenanta.document.aws.shared-s3.bucket-path", "tenant-a/");
    withProperties(properties);

    // when resolved
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then the untouched store is inherited whole
    assertThat(doc.getGcp()).containsKey("analytics-gcs");
    assertThat(doc.getGcp().get("analytics-gcs").getBucketName()).isEqualTo("analytics");
    assertThat(doc.getGcp().get("analytics-gcs").getPrefix()).isEqualTo("root/");
  }

  @Test
  void shouldKeepTenantPrivateStore() {
    // given a tenant declaring a store id that does not exist at the root
    final Map<String, Object> properties = new HashMap<>();
    properties.put("camunda.document.aws.shared-s3.bucket-name", "global-docs");
    properties.put(
        "camunda.physical-tenants.tenantb.document.azure.tenantb-blob.container-name", "tenantb");
    properties.put(
        "camunda.physical-tenants.tenantb.document.azure.tenantb-blob.container-path", "docs/");
    withProperties(properties);

    // when resolved
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenantb", environment);

    // then the tenant-private store survives the union alongside the inherited one
    assertThat(doc.getAws()).containsKey("shared-s3");
    assertThat(doc.getAzure()).containsKey("tenantb-blob");
    assertThat(doc.getAzure().get("tenantb-blob").getContainerName()).isEqualTo("tenantb");
  }

  @Test
  void shouldNarrowToAssignedWhenDeclared() {
    // given a root catalog of two stores and a tenant restricting to a subset
    final Map<String, Object> properties = new HashMap<>();
    properties.put("camunda.document.aws.shared-s3.bucket-name", "global-docs");
    properties.put("camunda.document.gcp.analytics-gcs.bucket-name", "analytics");
    properties.put("camunda.physical-tenants.tenantb.document.assigned[0]", "shared-s3");
    withProperties(properties);

    // when resolved
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenantb", environment);

    // then only the assigned store remains; the unlisted one is dropped
    assertThat(doc.getAws()).containsKey("shared-s3");
    assertThat(doc.getGcp()).doesNotContainKey("analytics-gcs");
  }

  @Test
  void shouldKeepFullUnionWhenNoAssigned() {
    // given a root catalog and a tenant with NO assigned list
    final Map<String, Object> properties = new HashMap<>();
    properties.put("camunda.document.aws.shared-s3.bucket-name", "global-docs");
    properties.put("camunda.document.gcp.analytics-gcs.bucket-name", "analytics");
    properties.put(
        "camunda.physical-tenants.tenanta.document.aws.shared-s3.bucket-path", "tenant-a/");
    withProperties(properties);

    // when resolved
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then the full union is kept (selection is optional, unlike OIDC)
    assertThat(doc.getAws()).containsKey("shared-s3");
    assertThat(doc.getGcp()).containsKey("analytics-gcs");
  }

  @Test
  void shouldIgnoreAssignedDeclaredAtRoot() {
    // given an `assigned` declared at the ROOT (meaningless) and a tenant that declares none
    final Map<String, Object> properties = new HashMap<>();
    properties.put("camunda.document.aws.shared-s3.bucket-name", "global-docs");
    properties.put("camunda.document.gcp.analytics-gcs.bucket-name", "analytics");
    properties.put("camunda.document.assigned[0]", "shared-s3"); // ignored at root
    properties.put(
        "camunda.physical-tenants.tenanta.document.aws.shared-s3.bucket-path", "tenant-a/");
    withProperties(properties);

    // when resolved
    final Document doc =
        PhysicalTenantDocumentConfigurations.forPhysicalTenant("tenanta", environment);

    // then the root `assigned` does NOT narrow the tenant — the full union is kept
    assertThat(doc.getAws()).containsKey("shared-s3");
    assertThat(doc.getGcp()).containsKey("analytics-gcs");
  }
}
