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
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for the {@link SecondaryStorageIsolationValidation} cross-tenant rule: no two physical
 * tenants may resolve to the same {@link StorageIdentity} (they would silently double-write into
 * the same database location).
 *
 * <p>Hand-constructs {@link Camunda} instances directly — no Spring, no environment. With no custom
 * environment pinned, the legacy-fallback getters ({@code getType}, {@code getUrl}, {@code
 * getIndexPrefix}, ...) return the raw field values, so these tests exercise the extraction and
 * grouping logic in isolation.
 */
class SecondaryStorageIsolationValidationTest {

  private final SecondaryStorageIsolationValidation validation =
      new SecondaryStorageIsolationValidation();

  @Test
  void shouldRejectTwoTenantsSharingSameElasticsearchUrlAndIndexPrefix() {
    // given two tenants pointing at the same ES cluster with the same index prefix
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", "shared"),
            "tenantb", es("http://es:9200", "shared"));

    // when / then the collision is rejected, naming both tenants and the shared location
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageContaining("http://es:9200");
  }

  @Test
  void shouldPassWhenSameElasticsearchUrlButDifferentIndexPrefix() {
    // given two tenants on the same ES cluster but with distinct index prefixes
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", "tenant-a"),
            "tenantb", es("http://es:9200", "tenant-b"));

    // when / then the "shared cluster, distinct prefix" setup is allowed
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectTwoTenantsBothInheritingRootStorage() {
    // given two tenants that both inherit the root storage (neither overrides anything):
    // identical defaults (type=elasticsearch, default url, empty index prefix) collide
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", new Camunda(),
            "tenantb", new Camunda());

    // when / then the inheritance collision is rejected
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "shared"})
  void shouldRejectWhenDefaultTenantSharesIdentityWithExplicitTenant(final String indexPrefix) {
    // given the synthesized 'default' tenant and an explicit tenant resolve to the same location —
    // covering both the empty-prefix default and an explicit shared prefix
    final Map<String, Camunda> resolved =
        tenants(
            "default", es("http://es:9200", indexPrefix),
            "tenanta", es("http://es:9200", indexPrefix));

    // when / then 'default' participates like any other tenant
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("default")
        .withMessageContaining("tenanta");
  }

  @Test
  void shouldPassForSingleTenantMap() {
    // given a single-tenant deployment (only the default tenant)
    final Map<String, Camunda> resolved = tenants("default", es("http://es:9200", ""));

    // when / then uniqueness over one entry is a no-op
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldReportOneGroupedErrorWhenThreeTenantsShareOneIdentity() {
    // given three tenants all sharing the same identity
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://es:9200", "shared"),
            "tenantb", es("http://es:9200", "shared"),
            "tenantc", es("http://es:9200", "shared"));

    // when / then one grouped error lists all three (not three pairwise errors)
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageContaining("tenantc")
        // a single grouped message mentions the shared location exactly once
        .satisfies(
            e -> assertThat(countOccurrences(e.getMessage(), "http://es:9200")).isEqualTo(1));
  }

  @Test
  void shouldRejectRdbmsTenantsSharingSameJdbcUrlAndPrefix() {
    // given two RDBMS tenants on the same jdbc url with the same table prefix
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", rdbms("jdbc:postgresql://db:5432/camunda", "shared"),
            "tenantb", rdbms("jdbc:postgresql://db:5432/camunda", "shared"));

    // when / then the collision is rejected
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb");
  }

  @Test
  void shouldPassForRdbmsTenantsSharingJdbcUrlButDifferentPrefix() {
    // given two RDBMS tenants on the same jdbc url but with distinct table prefixes
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", rdbms("jdbc:postgresql://db:5432/camunda", "a_"),
            "tenantb", rdbms("jdbc:postgresql://db:5432/camunda", "b_"));

    // when / then distinct prefixes isolate the tenants
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldPassForRdbmsTenantsOnSameDbWithDifferentSchemasInUrl() {
    // given a typical RDBMS multitenancy setup: two tenants on the same database server using
    // distinct schemas encoded in the jdbc url, and no table prefix
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", rdbms("jdbc:postgresql://db:5432/camunda?currentSchema=tenanta", null),
            "tenantb", rdbms("jdbc:postgresql://db:5432/camunda?currentSchema=tenantb", null));

    // when / then the differing schema in the url isolates the tenants — no collision
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldPassWhenTenantsDifferOnlyByStorageType() {
    // given one ES and one RDBMS tenant whose connection strings happen to look alike
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", es("http://same:9200", "shared"),
            "tenantb", rdbms("http://same:9200", "shared"));

    // when / then the storage type discriminates — no collision
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldNeverCollideForTypeNoneTenants() {
    // given several tenants with no secondary storage at all
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", typeNone(),
            "tenantb", typeNone(),
            "default", typeNone());

    // when / then type=none yields no storage identity and never collides
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldSurfaceMisconfiguredRdbmsTenantsLeftAtDefaultUrl() {
    // given two RDBMS tenants that never set a jdbc url: they both inherit the (ES) default url and
    // the null table prefix, so the extractor lets the collision surface the misconfiguration as an
    // error rather than silently treating it as "no identity".
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", rdbmsDefaults(),
            "tenantb", rdbmsDefaults());

    // when / then the shared default location is reported as a collision
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb");
  }

  // --- helpers ---------------------------------------------------------------------------------

  private static Camunda es(final String url, final String indexPrefix) {
    final Camunda camunda = new Camunda();
    final var ss = camunda.getData().getSecondaryStorage();
    ss.setType(SecondaryStorageType.elasticsearch);
    ss.getElasticsearch().setUrl(url);
    ss.getElasticsearch().setIndexPrefix(indexPrefix);
    return camunda;
  }

  private static Camunda rdbms(final String jdbcUrl, final String prefix) {
    final Camunda camunda = new Camunda();
    final var ss = camunda.getData().getSecondaryStorage();
    ss.setType(SecondaryStorageType.rdbms);
    ss.getRdbms().setUrl(jdbcUrl);
    ss.getRdbms().setPrefix(prefix);
    return camunda;
  }

  private static Camunda rdbmsDefaults() {
    final Camunda camunda = new Camunda();
    camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.rdbms);
    return camunda;
  }

  private static Camunda typeNone() {
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
