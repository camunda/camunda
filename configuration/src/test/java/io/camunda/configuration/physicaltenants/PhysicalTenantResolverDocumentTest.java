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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * SPIKE (POC) — proves the per-tenant document overlay is wired end-to-end into {@link
 * PhysicalTenantResolver#of}: the recompute runs per tenant and {@code
 * DocumentStoreIsolationValidation} fails boot on a location collision (#54366).
 */
class PhysicalTenantResolverDocumentTest {

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

  /** Per-PT prerequisites required by the resolver's other validations, unrelated to documents. */
  private void addTenantPrerequisites(final Map<String, Object> properties, final String tenantId) {
    // distinct secondary-storage location so SecondaryStorageIsolationValidation does not fire
    // first
    properties.put(
        "camunda.physical-tenants."
            + tenantId
            + ".data.secondary-storage.elasticsearch.index-prefix",
        tenantId);
    // each PT must provide its own initialization block (required per-PT override)
    properties.put(
        "camunda.physical-tenants."
            + tenantId
            + ".security.initialization.default-roles.admin.users[0]",
        tenantId + "-admin");
  }

  private Camunda resolveRoot() {
    final Camunda camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return camunda;
  }

  @Test
  void shouldFailBootWhenTwoTenantsInheritSameStoreUnchanged() {
    // given a root store that both tenants inherit WITHOUT overriding its path
    final Map<String, Object> properties = new HashMap<>();
    properties.put("camunda.document.aws.shared-s3.bucket-name", "global-docs");
    properties.put("camunda.document.aws.shared-s3.bucket-path", "root/");
    addTenantPrerequisites(properties, "tenanta");
    addTenantPrerequisites(properties, "tenantb");
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));

    // when / then resolution fails fast: both tenants (and 'default') resolve to global-docs:root/
    assertThatThrownBy(() -> PhysicalTenantResolver.of(environment, resolveRoot()))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("document-store location");
  }

  @Test
  void shouldResolveWhenEachTenantOverridesPathDistinctly() {
    // given each tenant overriding ONLY the bucket-path to a distinct value
    final Map<String, Object> properties = new HashMap<>();
    properties.put("camunda.document.aws.shared-s3.bucket-name", "global-docs");
    properties.put("camunda.document.aws.shared-s3.bucket-path", "root/");
    properties.put(
        "camunda.physical-tenants.tenanta.document.aws.shared-s3.bucket-path", "tenant-a/");
    properties.put(
        "camunda.physical-tenants.tenantb.document.aws.shared-s3.bucket-path", "tenant-b/");
    addTenantPrerequisites(properties, "tenanta");
    addTenantPrerequisites(properties, "tenantb");
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));

    // when resolved
    final PhysicalTenantResolver resolver = PhysicalTenantResolver.of(environment, resolveRoot());

    // then it boots, and each tenant has the field-merged store (name inherited, path overridden)
    final Camunda tenantA = resolver.forPhysicalTenant("tenanta");
    assertThat(tenantA.getDocument().getAws().get("shared-s3").getBucketName())
        .isEqualTo("global-docs");
    assertThat(tenantA.getDocument().getAws().get("shared-s3").getBucketPath())
        .isEqualTo("tenant-a/");
  }

  @Test
  void shouldResolveWhenTenantDropsCollidingStoreViaAssigned() {
    // given a root store both tenants would inherit, but tenantb drops it and brings its own
    final Map<String, Object> properties = new HashMap<>();
    properties.put("camunda.document.aws.shared-s3.bucket-name", "global-docs");
    properties.put("camunda.document.aws.shared-s3.bucket-path", "root/");
    // tenanta overrides the path; tenantb restricts to its own private store, dropping shared-s3
    properties.put(
        "camunda.physical-tenants.tenanta.document.aws.shared-s3.bucket-path", "tenant-a/");
    properties.put("camunda.physical-tenants.tenantb.document.assigned[0]", "tenantb-blob");
    properties.put(
        "camunda.physical-tenants.tenantb.document.azure.tenantb-blob.container-name", "tenantb");
    properties.put(
        "camunda.physical-tenants.tenantb.document.azure.tenantb-blob.container-path", "docs/");
    addTenantPrerequisites(properties, "tenanta");
    addTenantPrerequisites(properties, "tenantb");
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));

    // when resolved
    final PhysicalTenantResolver resolver = PhysicalTenantResolver.of(environment, resolveRoot());

    // then tenantb dropped the inherited store (no collision with 'default') and kept its private
    // one
    final Camunda tenantB = resolver.forPhysicalTenant("tenantb");
    assertThat(tenantB.getDocument().getAws()).doesNotContainKey("shared-s3");
    assertThat(tenantB.getDocument().getAzure()).containsKey("tenantb-blob");
  }

  @Test
  void shouldResolveSingleTenantWithoutCollision() {
    final Map<String, Object> properties = new HashMap<>();
    properties.put("camunda.document.aws.shared-s3.bucket-name", "global-docs");
    properties.put("camunda.document.aws.shared-s3.bucket-path", "root/");
    addTenantPrerequisites(properties, "tenanta");
    properties.put(
        "camunda.physical-tenants.tenanta.document.aws.shared-s3.bucket-path", "tenant-a/");
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));

    assertThatCode(() -> PhysicalTenantResolver.of(environment, resolveRoot()))
        .doesNotThrowAnyException();
  }
}
