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
 * Unit tests for the {@link SecondaryStorageTypeHomogeneityValidation} cross-tenant rule: all
 * physical tenants must resolve to a single secondary-storage <em>compatibility class</em>.
 *
 * <p>Classes (agreed scope): Elasticsearch and OpenSearch share one document-store class and may
 * mix across tenants; RDBMS is its own class and may not mix with document stores; {@code none} is
 * its own class and must agree too (a {@code none} tenant alongside a storage-backed tenant is
 * rejected).
 */
class SecondaryStorageTypeHomogeneityValidationTest {

  private final SecondaryStorageTypeHomogeneityValidation validation =
      new SecondaryStorageTypeHomogeneityValidation();

  @BeforeEach
  void setUp() {
    UnifiedConfigurationHelper.setCustomEnvironment(new MockEnvironment());
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldPassWhenAllTenantsUseElasticsearch() {
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", type(SecondaryStorageType.elasticsearch),
            "tenantb", type(SecondaryStorageType.elasticsearch));

    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldPassWhenTenantsMixElasticsearchAndOpensearch() {
    // ES and OS are the same document-store compatibility class
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", type(SecondaryStorageType.elasticsearch),
            "tenantb", type(SecondaryStorageType.opensearch));

    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectMixingDocumentStoreWithRdbms() {
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", type(SecondaryStorageType.elasticsearch),
            "tenantb", type(SecondaryStorageType.rdbms));

    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb");
  }

  @Test
  void shouldPassWhenAllTenantsUseRdbms() {
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", type(SecondaryStorageType.rdbms),
            "tenantb", type(SecondaryStorageType.rdbms));

    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectMixingTypeNoneWithStorageBackedTenant() {
    // 'none' must agree too: it is its own class
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", type(SecondaryStorageType.elasticsearch),
            "tenantb", type(SecondaryStorageType.none));

    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb");
  }

  @Test
  void shouldPassWhenAllTenantsUseTypeNone() {
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", type(SecondaryStorageType.none),
            "tenantb", type(SecondaryStorageType.none));

    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldPassForSingleTenantMap() {
    final Map<String, Camunda> resolved = tenants("default", type(SecondaryStorageType.rdbms));

    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  // --- helpers ---------------------------------------------------------------------------------

  private static Camunda type(final SecondaryStorageType type) {
    final Camunda camunda = new Camunda();
    camunda.getData().getSecondaryStorage().setType(type);
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
