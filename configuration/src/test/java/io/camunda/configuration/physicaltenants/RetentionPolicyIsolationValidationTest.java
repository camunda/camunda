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

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit tests for the {@link RetentionPolicyIsolationValidation} cross-tenant rule: no two physical
 * tenants with retention enabled may resolve to the same {@link RetentionPolicyIdentity} (they
 * would overwrite each other's cluster-global ILM/ISM lifecycle policy).
 */
class RetentionPolicyIsolationValidationTest {

  private static final String DEFAULT_POLICY = "camunda-retention-policy";

  private final RetentionPolicyIsolationValidation validation =
      new RetentionPolicyIsolationValidation();

  @BeforeEach
  void setUp() {
    UnifiedConfigurationHelper.setCustomEnvironment(new MockEnvironment());
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldRejectTenantsSharingClusterAndDefaultPolicyName() {
    // given two retention-enabled tenants on the same cluster, both left at the default policy name
    // (the headline footgun: distinct index prefixes pass the isolation rule, yet the ILM policy is
    // cluster-global and would be overwritten)
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", true, null),
            "tenantb", es("http://es:9200", true, null));

    // when / then the shared lifecycle policy is rejected, naming both tenants and the policy
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageContaining("http://es:9200")
        .withMessageContaining(DEFAULT_POLICY);
  }

  @Test
  void shouldRejectTenantsSharingClusterAndExplicitPolicyName() {
    // given two retention-enabled tenants on the same cluster with the same explicit policy name
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", true, "shared-policy"),
            "tenantb", es("http://es:9200", true, "shared-policy"));

    // when / then the collision is rejected
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageContaining("shared-policy");
  }

  @Test
  void shouldPassWhenSharedClusterButDistinctPolicyNames() {
    // given two retention-enabled tenants on the same cluster with distinct policy names
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", true, "tenant-a-policy"),
            "tenantb", es("http://es:9200", true, "tenant-b-policy"));

    // when / then distinct policy names isolate the tenants — no collision
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldPassWhenSamePolicyNameButDistinctClusters() {
    // given two retention-enabled tenants using the same policy name but on distinct clusters
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es-a:9200", true, DEFAULT_POLICY),
            "tenantb", es("http://es-b:9200", true, DEFAULT_POLICY));

    // when / then distinct clusters mean the policies never overwrite each other — no collision
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldIgnoreTenantsWithRetentionDisabled() {
    // given two tenants that would collide, but both have retention disabled
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", false, DEFAULT_POLICY),
            "tenantb", es("http://es:9200", false, DEFAULT_POLICY));

    // when / then retention disabled → no lifecycle policy is created → no collision
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldOnlyConsiderRetentionEnabledTenants() {
    // given two tenants sharing cluster + policy name, but only one has retention enabled
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", true, DEFAULT_POLICY),
            "tenantb", es("http://es:9200", false, DEFAULT_POLICY));

    // when / then only the retention-enabled tenant participates → single participant → no
    // collision
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldPassForSingleTenantMap() {
    // given a single-tenant deployment with retention enabled
    final Map<String, Camunda> resolved =
        tenants("default", es("http://es:9200", true, DEFAULT_POLICY));

    // when / then uniqueness over one entry is a no-op
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectWhenDefaultTenantSharesPolicyWithExplicitTenant() {
    // given the synthesized 'default' tenant and an explicit tenant resolve to the same policy
    final Map<String, Camunda> resolved =
        tenants(
            "default", es("http://es:9200", true, DEFAULT_POLICY),
            "tenanta", es("http://es:9200", true, DEFAULT_POLICY));

    // when / then 'default' participates like any other tenant
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("default")
        .withMessageContaining("tenanta");
  }

  @Test
  void shouldReportOneGroupedErrorWhenThreeTenantsShareOnePolicy() {
    // given three retention-enabled tenants all sharing the same cluster + policy name
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", true, DEFAULT_POLICY),
            "tenantb", es("http://es:9200", true, DEFAULT_POLICY),
            "tenantc", es("http://es:9200", true, DEFAULT_POLICY));

    // when / then one grouped error lists all three (not three pairwise errors)
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageContaining("tenantc")
        // a single grouped message mentions the shared policy exactly once
        .satisfies(e -> assertThat(countOccurrences(e.getMessage(), DEFAULT_POLICY)).isEqualTo(1));
  }

  @Test
  void shouldRejectOpensearchTenantsSharingClusterAndPolicyName() {
    // given two retention-enabled OpenSearch tenants on the same cluster with the same policy name
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", os("http://os:9200", true, DEFAULT_POLICY),
            "tenantb", os("http://os:9200", true, DEFAULT_POLICY));

    // when / then the OpenSearch (ISM) collision is rejected
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageContaining("opensearch");
  }

  @Test
  void shouldTreatElasticsearchAndOpensearchAsDistinctLocations() {
    // given one ES and one OS tenant whose urls and policy names happen to match
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://same:9200", true, DEFAULT_POLICY),
            "tenantb", os("http://same:9200", true, DEFAULT_POLICY));

    // when / then the engine type discriminates (ILM vs ISM subsystems) — no collision
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldIgnoreRdbmsAndNoneTenants() {
    // given rdbms/none tenants with retention enabled — they have no ILM/ISM lifecycle policy
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", rdbmsRetentionEnabled(),
            "tenantb", rdbmsRetentionEnabled(),
            "tenantc", noneRetentionEnabled());

    // when / then non-document stores never create a lifecycle policy → no collision
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldTrimPolicyNameWhitespace() {
    // given two tenants whose policy names differ only by surrounding whitespace — a config typo
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", true, "policy"),
            "tenantb", es("http://es:9200", true, "  policy  "));

    // when / then trimming folds them together → the likely-unintended collision is surfaced
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb");
  }

  // --- helpers -----------------------------------------------------------------------------------

  private static Camunda es(
      final String url, final boolean retentionEnabled, final String policyName) {
    return documentBased(SecondaryStorageType.elasticsearch, url, retentionEnabled, policyName);
  }

  private static Camunda os(
      final String url, final boolean retentionEnabled, final String policyName) {
    return documentBased(SecondaryStorageType.opensearch, url, retentionEnabled, policyName);
  }

  private static Camunda documentBased(
      final SecondaryStorageType type,
      final String url,
      final boolean retentionEnabled,
      final String policyName) {
    final Camunda camunda = new Camunda();
    final var ss = camunda.getData().getSecondaryStorage();
    ss.setType(type);
    ss.getRetention().setEnabled(retentionEnabled);
    final var database =
        type == SecondaryStorageType.elasticsearch ? ss.getElasticsearch() : ss.getOpensearch();
    database.setUrl(url);
    if (policyName != null) {
      database.getHistory().setPolicyName(policyName);
    }
    return camunda;
  }

  private static Camunda rdbmsRetentionEnabled() {
    final Camunda camunda = new Camunda();
    final var ss = camunda.getData().getSecondaryStorage();
    ss.setType(SecondaryStorageType.rdbms);
    ss.getRdbms().setUrl("jdbc:postgresql://db:5432/camunda");
    ss.getRetention().setEnabled(true);
    return camunda;
  }

  private static Camunda noneRetentionEnabled() {
    final Camunda camunda = new Camunda();
    final var ss = camunda.getData().getSecondaryStorage();
    ss.setType(SecondaryStorageType.none);
    ss.getRetention().setEnabled(true);
    return camunda;
  }

  private static Map<String, Camunda> tenants(final Object... idThenCamunda) {
    final Map<String, Camunda> map = new LinkedHashMap<>();
    for (int i = 0; i < idThenCamunda.length; i += 2) {
      map.put((String) idThenCamunda[i], (Camunda) idThenCamunda[i + 1]);
    }
    return map;
  }

  private static int countOccurrences(final String haystack, final String needle) {
    int count = 0;
    int from = 0;
    while ((from = haystack.indexOf(needle, from)) >= 0) {
      count++;
      from += needle.length();
    }
    return count;
  }
}
