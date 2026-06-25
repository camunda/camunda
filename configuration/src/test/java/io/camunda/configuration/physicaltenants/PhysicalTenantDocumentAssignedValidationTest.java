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
import io.camunda.configuration.Document;
import io.camunda.configuration.Document.AwsStore;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantDocumentAssignedValidationTest {

  private final PhysicalTenantDocumentAssignedValidation validation =
      new PhysicalTenantDocumentAssignedValidation();

  @BeforeEach
  void setUp() {
    UnifiedConfigurationHelper.setCustomEnvironment(new MockEnvironment());
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldPassWhenNonDefaultTenantHasNoDocumentStores() {
    // given a non-default tenant with no document stores in its catalog
    final Camunda camunda = new Camunda();
    camunda.getDocument().setAssigned(new ArrayList<>());
    final Map<String, Camunda> resolved = tenants("tenanta", camunda);

    // when / then no stores → nothing to assign → validation is a no-op
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldPassWhenDefaultTenantOmitsAssigned() {
    // given
    final Map<String, Camunda> resolved =
        tenants(
            PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, camundaWithStore("store-a", List.of()));

    // when / then
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldFailWhenNonDefaultTenantOmitsAssigned() {
    // given
    final Map<String, Camunda> resolved =
        tenants("tenanta", camundaWithStore("store-a", List.of()));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("must declare a non-empty");
  }

  @Test
  void shouldFailWhenAssignedContainsUnknownStoreId() {
    // given
    final Map<String, Camunda> resolved =
        tenants("tenanta", camundaWithStore("store-a", List.of("unknown-id")));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("unknown-id");
  }

  @Test
  void shouldPassWhenAssignedContainsOnlyKnownIds() {
    // given
    final Map<String, Camunda> resolved =
        tenants("tenanta", camundaWithStore("store-a", List.of("store-a")));

    // when / then
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldFailOnBlankAssignedEntry() {
    // given blank entry (e.g. `- ""` in yaml) — must be rejected, not treated as unknown id
    final Map<String, Camunda> resolved =
        tenants("tenanta", camundaWithStore("store-a", List.of("")));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("blank entry");
  }

  @Test
  void shouldPassWhenDefaultTenantIsExemptInMultiTenantDeployment() {
    // given
    final Camunda defaultTenant = camundaWithStore("store-default", List.of());
    final Camunda tenantA = camundaWithStore("store-a", List.of("store-a"));
    final Map<String, Camunda> resolved =
        tenants(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, defaultTenant, "tenanta", tenantA);

    // when / then
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  // --- helpers ---------------------------------------------------------------------------------

  private static Camunda camundaWithStore(final String storeId, final List<String> assigned) {
    final Camunda camunda = new Camunda();
    final Document doc = camunda.getDocument();

    final AwsStore store = new AwsStore();
    store.setBucketName("bucket-" + storeId);
    store.setBucketPath("path/" + storeId);
    final Map<String, AwsStore> aws = new LinkedHashMap<>();
    aws.put(storeId, store);
    doc.setAws(aws);
    doc.setAssigned(new ArrayList<>(assigned));
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
