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
 * Unit tests for the {@link SnapshotRepositoryIsolationValidation} cross-tenant rule: no two
 * physical tenants may resolve to the same {@link SnapshotRepositoryIdentity} (their history
 * snapshots would share one repository, which is the only thing separating them).
 */
class SnapshotRepositoryIsolationValidationTest {

  private static final String SHARED_REPOSITORY = "camunda-backup";

  private final SnapshotRepositoryIsolationValidation validation =
      new SnapshotRepositoryIsolationValidation();

  @BeforeEach
  void setUp() {
    UnifiedConfigurationHelper.setCustomEnvironment(new MockEnvironment());
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldRejectTenantsSharingClusterAndRepositoryName() {
    // given two tenants on the same cluster pointed at the same repository — the headline footgun:
    // distinct index prefixes pass the isolation rule, yet the repository is cluster-global
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", SHARED_REPOSITORY),
            "tenantb", es("http://es:9200", SHARED_REPOSITORY));

    // when / then the shared repository is rejected, naming both tenants and the repository
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageContaining("http://es:9200")
        .withMessageContaining(SHARED_REPOSITORY);
  }

  @Test
  void shouldPassWhenSharedClusterButDistinctRepositoryNames() {
    // given two tenants on the same cluster with a repository each
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", "tenant-a-backup"),
            "tenantb", es("http://es:9200", "tenant-b-backup"));

    // when / then distinct repositories isolate the tenants' snapshots — no collision
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldPassWhenSameRepositoryNameButDistinctClusters() {
    // given two tenants using the same repository name on distinct clusters
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es-a:9200", SHARED_REPOSITORY),
            "tenantb", es("http://es-b:9200", SHARED_REPOSITORY));

    // when / then a repository is scoped to its cluster, so the two never overlap — no collision
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldIgnoreTenantsWithoutAConfiguredRepository() {
    // given two tenants on the same cluster that configure no repository at all
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", null),
            "tenantb", es("http://es:9200", "   "));

    // when / then a tenant with no repository takes no snapshots — it is skipped, not rejected
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldOnlyConsiderTenantsWithAConfiguredRepository() {
    // given two tenants on one cluster, only one of which configures a repository
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", SHARED_REPOSITORY),
            "tenantb", es("http://es:9200", null));

    // when / then a single participant cannot collide
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldPassForSingleTenantMap() {
    // given a single-tenant deployment
    final Map<String, Camunda> resolved =
        tenants("default", es("http://es:9200", SHARED_REPOSITORY));

    // when / then uniqueness over one entry is a no-op
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectWhenDefaultTenantSharesRepositoryWithExplicitTenant() {
    // given the synthesized 'default' tenant and an explicit tenant resolve to the same repository
    // — what inheriting a root repository-name produces
    final Map<String, Camunda> resolved =
        tenants(
            "default", es("http://es:9200", SHARED_REPOSITORY),
            "tenanta", es("http://es:9200", SHARED_REPOSITORY));

    // when / then 'default' participates like any other tenant
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("default")
        .withMessageContaining("tenanta");
  }

  @Test
  void shouldReportOneGroupedErrorWhenThreeTenantsShareOneRepository() {
    // given three tenants all sharing the same cluster + repository name
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", SHARED_REPOSITORY),
            "tenantb", es("http://es:9200", SHARED_REPOSITORY),
            "tenantc", es("http://es:9200", SHARED_REPOSITORY));

    // when / then one grouped error lists all three (not three pairwise errors)
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageContaining("tenantc")
        // a single grouped message mentions the shared repository exactly once
        .satisfies(
            e -> assertThat(countOccurrences(e.getMessage(), SHARED_REPOSITORY)).isEqualTo(1));
  }

  @Test
  void shouldRejectOpensearchTenantsSharingClusterAndRepositoryName() {
    // given two OpenSearch tenants on the same cluster pointed at the same repository
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", os("http://os:9200", SHARED_REPOSITORY),
            "tenantb", os("http://os:9200", SHARED_REPOSITORY));

    // when / then the OpenSearch collision is rejected too
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageContaining("opensearch");
  }

  @Test
  void shouldTreatElasticsearchAndOpensearchAsDistinctLocations() {
    // given one ES and one OS tenant whose urls and repository names happen to match
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://same:9200", SHARED_REPOSITORY),
            "tenantb", os("http://same:9200", SHARED_REPOSITORY));

    // when / then the engine type discriminates — no collision
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldIgnoreRdbmsAndNoneTenants() {
    // given rdbms/none tenants — they take no ES/OS snapshots
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", rdbms(),
            "tenantb", rdbms(),
            "tenantc", none());

    // when / then non-document stores have no snapshot repository → no collision
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldTrimRepositoryNameWhitespace() {
    // given two tenants whose repository names differ only by surrounding whitespace — a config
    // typo, not isolation
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", "backup"),
            "tenantb", es("http://es:9200", "  backup  "));

    // when / then trimming folds them together → the collision is surfaced
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb");
  }

  @Test
  void shouldNotFoldRepositoryNamesThatDifferOnlyByCase() {
    // given two tenants whose repository names differ only by case
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", "Backup"),
            "tenantb", es("http://es:9200", "backup"));

    // when / then ES/OS repository names are case-sensitive, so these are two repositories
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  // --- helpers -----------------------------------------------------------------------------------

  private static Camunda es(final String url, final String repositoryName) {
    return documentBased(SecondaryStorageType.elasticsearch, url, repositoryName);
  }

  private static Camunda os(final String url, final String repositoryName) {
    return documentBased(SecondaryStorageType.opensearch, url, repositoryName);
  }

  private static Camunda documentBased(
      final SecondaryStorageType type, final String url, final String repositoryName) {
    final Camunda camunda = new Camunda();
    final var ss = camunda.getData().getSecondaryStorage();
    ss.setType(type);
    final var database =
        type == SecondaryStorageType.elasticsearch ? ss.getElasticsearch() : ss.getOpensearch();
    database.setUrl(url);
    database.getBackup().setRepositoryName(repositoryName);
    return camunda;
  }

  private static Camunda rdbms() {
    final Camunda camunda = new Camunda();
    final var ss = camunda.getData().getSecondaryStorage();
    ss.setType(SecondaryStorageType.rdbms);
    ss.getRdbms().setUrl("jdbc:postgresql://db:5432/camunda");
    return camunda;
  }

  private static Camunda none() {
    final Camunda camunda = new Camunda();
    camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
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
