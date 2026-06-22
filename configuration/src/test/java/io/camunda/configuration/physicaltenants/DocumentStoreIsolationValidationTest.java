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
import io.camunda.configuration.Document.AwsStore;
import io.camunda.configuration.Document.InMemoryStore;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * SPIKE (POC) — proves the cross-tenant document-store location collision check. Mirrors {@code
 * SecondaryStorageIsolationValidationTest}: hard-fail when two tenants resolve a store to the same
 * physical location, pass when locations differ.
 */
class DocumentStoreIsolationValidationTest {

  private final DocumentStoreIsolationValidation validation =
      new DocumentStoreIsolationValidation();

  private static Camunda withAws(
      final String bucketName, final String bucketPath, final String region) {
    final Camunda camunda = new Camunda();
    final AwsStore store = new AwsStore();
    store.setBucketName(bucketName);
    store.setBucketPath(bucketPath);
    store.setRegion(region);
    camunda.getDocument().getAws().put("s3", store);
    return camunda;
  }

  @Test
  void shouldFailWhenTwoTenantsShareBucketAndPath() {
    // given two tenants whose stores resolve to the same bucket + path
    final Map<String, Camunda> resolved = new LinkedHashMap<>();
    resolved.put("tenanta", withAws("docs", "shared/", "eu-west-1"));
    resolved.put("tenantb", withAws("docs", "shared/", "eu-west-1"));

    // when / then the check hard-fails naming both tenants
    assertThatThrownBy(() -> validation.validate(resolved))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("tenanta")
        .hasMessageContaining("tenantb")
        .hasMessageContaining("document-store location");
  }

  @Test
  void shouldPassWhenPathsDiffer() {
    // given two tenants sharing a bucket but with distinct paths (the blessed pattern)
    final Map<String, Camunda> resolved = new LinkedHashMap<>();
    resolved.put("tenanta", withAws("docs", "tenant-a/", "eu-west-1"));
    resolved.put("tenantb", withAws("docs", "tenant-b/", "eu-west-1"));

    // when / then no collision
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldFailIgnoringRegionDifference() {
    // given same bucket + path but DIFFERENT region — region is not part of the location identity
    final Map<String, Camunda> resolved = new LinkedHashMap<>();
    resolved.put("tenanta", withAws("docs", "shared/", "eu-west-1"));
    resolved.put("tenantb", withAws("docs", "shared/", "us-east-1"));

    // when / then it still collides: region must not mask a real collision
    assertThatThrownBy(() -> validation.validate(resolved))
        .isInstanceOf(UnifiedConfigurationException.class);
  }

  @Test
  void shouldTreatOmittedAndTrailingSlashPathAsEqual() {
    // given one tenant with bucket-path "shared" and another with "shared/" (normalization)
    final Map<String, Camunda> resolved = new LinkedHashMap<>();
    resolved.put("tenanta", withAws("docs", "shared", "eu-west-1"));
    resolved.put("tenantb", withAws("docs", "shared/", "eu-west-1"));

    // when / then a trailing slash does not create a spurious distinct location
    assertThatThrownBy(() -> validation.validate(resolved))
        .isInstanceOf(UnifiedConfigurationException.class);
  }

  @Test
  void shouldNeverCollideOnInMemoryStores() {
    // given two tenants each with an in-memory store of the same id
    final Camunda a = new Camunda();
    a.getDocument().getInMemory().put("mem", new InMemoryStore());
    final Camunda b = new Camunda();
    b.getDocument().getInMemory().put("mem", new InMemoryStore());
    final Map<String, Camunda> resolved = new LinkedHashMap<>();
    resolved.put("tenanta", a);
    resolved.put("tenantb", b);

    // when / then in-memory stores are ephemeral per-instance and never collide
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldBeNoOpForSingleTenant() {
    final Map<String, Camunda> resolved = new LinkedHashMap<>();
    resolved.put("tenanta", withAws("docs", "shared/", "eu-west-1"));

    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldDistinguishProvidersWithSameCoordinates() {
    // given an aws store and (hypothetically) a gcp store with identical coordinate strings
    final Camunda a = withAws("docs", "shared/", "eu-west-1");
    final Camunda b = new Camunda();
    final var gcp = new io.camunda.configuration.Document.GcpStore();
    gcp.setBucketName("docs");
    gcp.setPrefix("shared/");
    b.getDocument().getGcp().put("g", gcp);
    final Map<String, Camunda> resolved = new LinkedHashMap<>();
    resolved.put("tenanta", a);
    resolved.put("tenantb", b);

    // when / then the provider discriminator keeps them distinct
    assertThat(resolved).hasSize(2);
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }
}
