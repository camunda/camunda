/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Document.AwsStore;
import io.camunda.configuration.Document.InMemoryStore;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DocumentStoreIsolationValidationTest {

  private final DocumentStoreIsolationValidation validation =
      new DocumentStoreIsolationValidation();

  @BeforeEach
  void setUp() {
    UnifiedConfigurationHelper.setCustomEnvironment(new MockEnvironment());
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldFailWhenTwoTenantsShareBucketAndPath() {
    // given
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", awsCamunda("my-bucket", "shared/path", "us-east-1"),
            "tenantb", awsCamunda("my-bucket", "shared/path", "us-east-1"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("must not share a document store location")
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb");
  }

  @Test
  void shouldPassWhenPathsDiffer() {
    // given
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", awsCamunda("my-bucket", "tenant-a/docs", "us-east-1"),
            "tenantb", awsCamunda("my-bucket", "tenant-b/docs", "us-east-1"));

    // when / then
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldFailIgnoringRegionDifference() {
    // given S3 bucket names are globally unique, so region is not part of the identity
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", awsCamunda("global-bucket", "shared/path", "us-east-1"),
            "tenantb", awsCamunda("global-bucket", "shared/path", "eu-west-1"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("must not share a document store location")
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb");
  }

  @Test
  void shouldTreatTrailingSlashAsEquivalentToNoSlash() {
    // given normalizer strips trailing slashes, so "shared" and "shared/" resolve to the same
    // location
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", awsCamunda("my-bucket", "shared", "us-east-1"),
            "tenantb", awsCamunda("my-bucket", "shared/", "us-east-1"));

    // when / then error reports normalized value "shared", not "shared/"
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("must not share a document store location")
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageNotContaining("shared/");
  }

  @Test
  void shouldNormalizeNullBucketPath() {
    // given normalize maps null → "" and "" → "", so they collide
    final Map<String, Camunda> resolved = new LinkedHashMap<>();
    resolved.put("tenanta", awsCamunda("docs", null, "eu-west-1"));
    resolved.put("tenantb", awsCamunda("docs", "", "eu-west-1"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("must not share a document store location")
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb");
  }

  @Test
  void shouldNeverCollideOnInMemoryStores() {
    // given in-memory stores are ephemeral and process-local, excluded from collision detection
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", inMemoryCamunda("mem-store"),
            "tenantb", inMemoryCamunda("mem-store"));

    // when / then
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldPassForSingleTenant() {
    // given
    final Map<String, Camunda> resolved =
        tenants("default", awsCamunda("my-bucket", "default/path", "us-east-1"));

    // when / then
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldFailWhenTenantInheritsRootLocationAndCollidesWithDefault() {
    // given a non-default tenant that omits a document override inherits the root location;
    // the resolved config is identical to the default tenant's — a collision must be rejected
    final Camunda defaultTenant = awsCamunda("shared-bucket", "root/path", "us-east-1");
    final Camunda tenantA = awsCamunda("shared-bucket", "root/path", "us-east-1");
    final Map<String, Camunda> resolved = tenants("default", defaultTenant, "tenanta", tenantA);

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("must not share a document store location")
        .withMessageContaining("default")
        .withMessageContaining("tenanta");
  }

  // --- helpers ---------------------------------------------------------------------------------

  private static Camunda awsCamunda(
      final String bucketName, final String bucketPath, final String region) {
    final Camunda camunda = new Camunda();
    final AwsStore store = new AwsStore();
    store.setBucketName(bucketName);
    store.setBucketPath(bucketPath);
    store.setRegion(region);
    final Map<String, AwsStore> aws = new LinkedHashMap<>();
    aws.put("shared-s3", store);
    camunda.getDocument().setAws(aws);
    return camunda;
  }

  private static Camunda inMemoryCamunda(final String storeId) {
    final Camunda camunda = new Camunda();
    final Map<String, InMemoryStore> inMemory = new LinkedHashMap<>();
    inMemory.put(storeId, new InMemoryStore());
    camunda.getDocument().setInMemory(inMemory);
    return camunda;
  }

  private static Map<String, Camunda> tenants(final Object... idThenCamunda) {
    final Map<String, Camunda> map = new LinkedHashMap<>();
    for (int i = 0; i < idThenCamunda.length; i += 2) {
      map.put((String) idThenCamunda[i], (Camunda) idThenCamunda[i + 1]);
    }
    return map;
  }
}
