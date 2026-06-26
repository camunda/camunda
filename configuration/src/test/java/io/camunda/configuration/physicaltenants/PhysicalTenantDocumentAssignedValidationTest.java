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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantDocumentAssignedValidationTest {

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
  void shouldPassWhenNonDefaultTenantHasNoDocumentStores() {
    assertThatCode(() -> validate(tenants("tenanta", new Camunda()), Set.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldPassWhenDefaultTenantOmitsAssigned() {
    // synthesized default (absent from explicitlyDeclared) is exempt
    final Map<String, Camunda> resolved =
        tenants(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, camundaWithStore("store-a"));
    assertThatCode(() -> validate(resolved, Set.of())).doesNotThrowAnyException();
  }

  @Test
  void shouldFailWhenNonDefaultTenantOmitsAssigned() {
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validate(tenants("tenanta", camundaWithStore("store-a")), Set.of()))
        .withMessageContaining("tenanta")
        .withMessageContaining("must declare a non-empty");
  }

  @Test
  void shouldFailWhenAssignedContainsUnknownStoreId() {
    environment.setProperty("camunda.physical-tenants.tenanta.document.assigned[0]", "unknown-id");
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validate(tenants("tenanta", camundaWithStore("store-a")), Set.of()))
        .withMessageContaining("tenanta")
        .withMessageContaining("unknown-id");
  }

  @Test
  void shouldPassWhenAssignedContainsOnlyKnownIds() {
    environment.setProperty("camunda.physical-tenants.tenanta.document.assigned[0]", "store-a");
    assertThatCode(() -> validate(tenants("tenanta", camundaWithStore("store-a")), Set.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptAssignedIdWithDifferentCaseOrWhitespace() {
    // " store-a " normalizes to "store-a" and passes; "Shared-S3" has no matching store and fails
    environment.setProperty("camunda.physical-tenants.tenanta.document.assigned[0]", "Shared-S3");
    environment.setProperty("camunda.physical-tenants.tenanta.document.assigned[1]", " store-a ");
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validate(tenants("tenanta", camundaWithStore("store-a")), Set.of()))
        .withMessageContaining("unknown document store id(s) [Shared-S3]");
  }

  @Test
  void shouldFailOnBlankAssignedEntry() {
    // blank entries must be caught before the unknown-id check; otherwise they render as `[]`
    environment.setProperty("camunda.physical-tenants.tenanta.document.assigned[0]", "");
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validate(tenants("tenanta", camundaWithStore("store-a")), Set.of()))
        .withMessageContaining("tenanta")
        .withMessageContaining("blank entry");
  }

  @Test
  void shouldPassWhenDefaultTenantIsExemptInMultiTenantDeployment() {
    environment.setProperty("camunda.physical-tenants.tenanta.document.assigned[0]", "store-a");
    final Map<String, Camunda> resolved =
        tenants(
            PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
            camundaWithStore("store-default"),
            "tenanta",
            camundaWithStore("store-a"));
    assertThatCode(() -> validate(resolved, Set.of())).doesNotThrowAnyException();
  }

  @Test
  void shouldFailWhenExplicitDefaultDeclaresStoresWithoutAssigned() {
    // explicitly declared default is validated like any other tenant
    final Map<String, Camunda> resolved =
        tenants(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, camundaWithStore("store-a"));
    final Set<String> explicitlyDeclared = Set.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validate(resolved, explicitlyDeclared))
        .withMessageContaining(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
        .withMessageContaining("must declare a non-empty");
  }

  @Test
  void shouldPassWhenExplicitDefaultAssignsKnownStore() {
    environment.setProperty(
        "camunda.physical-tenants."
            + PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID
            + ".document.assigned[0]",
        "store-a");
    final Map<String, Camunda> resolved =
        tenants(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, camundaWithStore("store-a"));
    final Set<String> explicitlyDeclared = Set.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThatCode(() -> validate(resolved, explicitlyDeclared)).doesNotThrowAnyException();
  }

  // --- helpers ---------------------------------------------------------------------------------

  private void validate(final Map<String, Camunda> resolved, final Set<String> explicitlyDeclared) {
    PhysicalTenantDocumentAssignedValidation.validate(environment, resolved, explicitlyDeclared);
  }

  private static Camunda camundaWithStore(final String storeId) {
    final Camunda camunda = new Camunda();
    final Document doc = camunda.getDocument();
    final AwsStore store = new AwsStore();
    store.setBucketName("bucket-" + storeId);
    store.setBucketPath("path/" + storeId);
    final Map<String, AwsStore> aws = new LinkedHashMap<>();
    aws.put(storeId, store);
    doc.setAws(aws);
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
