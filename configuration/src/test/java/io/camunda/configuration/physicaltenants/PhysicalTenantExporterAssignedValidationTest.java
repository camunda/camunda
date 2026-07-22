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

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Exporter;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit-tests the dormant {@code exporters-assigned} validation (ADR-0008 D1/D2/D5). Exercises the
 * validation class directly with a constructed resolved-by-tenant map (the class is not yet wired
 * into {@link PhysicalTenantResolver} — gated on #56652), mirroring {@link
 * PhysicalTenantDocumentAssignedValidationTest}.
 */
class PhysicalTenantExporterAssignedValidationTest {

  private static final String TENANT = "tenanta";

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
  void shouldPassWhenSynthesizedDefaultOmitsAssigned() {
    // synthesized default (absent from explicitlyDeclared) is exempt even with a catalog
    final Map<String, Camunda> resolved =
        tenants(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, camundaWithExporters("es"));
    assertThatCode(() -> validate(resolved, Set.of())).doesNotThrowAnyException();
  }

  @Test
  void shouldFailWhenNonDefaultTenantOmitsAssignedButCatalogHasGenericExporters() {
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validate(tenants(TENANT, camundaWithExporters("es")), Set.of()))
        .withMessageContaining(TENANT)
        .withMessageContaining("must declare");
  }

  @Test
  void shouldRequireAssignedWhenTenantDeclaresGenericExporterEvenWithEmptyRootCatalog() {
    // the mandatory rule fires from the tenant's own generic declaration too (empty root catalog):
    // the resolved map here holds only a tenant-declared exporter, and the manifest is absent
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validate(tenants(TENANT, camundaWithExporters("custom")), Set.of()))
        .withMessageContaining(TENANT)
        .withMessageContaining("must declare");
  }

  @Test
  void shouldPassWhenNoGenericExportersAndAssignedAbsent() {
    // only the autoconfigured exporter is present — no generic exporter could apply, key optional
    assertThatCode(
            () -> validate(tenants(TENANT, camundaWithExporters("camundaexporter")), Set.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldPassWhenAssignedIsEmptyAndTenantDeclaresNoGenericExporter() {
    // an explicit empty manifest is valid ("no generic exporters"); the inherited catalog entry is
    // narrowed away downstream, not a validation error
    environment.setProperty("camunda.physical-tenants.tenanta.data.exporters-assigned", "");
    assertThatCode(() -> validate(tenants(TENANT, camundaWithExporters("es")), Set.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldPassWhenAssignedMatchesCatalog() {
    environment.setProperty("camunda.physical-tenants.tenanta.data.exporters-assigned[0]", "es");
    assertThatCode(() -> validate(tenants(TENANT, camundaWithExporters("es")), Set.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldTreatDifferentlyCasedAssignedIdAsUnknown() {
    // exporter ids are case-sensitive (#36444): "Es" does not match the catalog id "es"
    environment.setProperty("camunda.physical-tenants.tenanta.data.exporters-assigned[0]", "Es");
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validate(tenants(TENANT, camundaWithExporters("es")), Set.of()))
        .withMessageContaining("unknown exporter id(s) [Es]");
  }

  @Test
  void shouldFailWhenAssignedListsAutoconfiguredId() {
    environment.setProperty(
        "camunda.physical-tenants.tenanta.data.exporters-assigned[0]", "camundaexporter");
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validate(tenants(TENANT, camundaWithExporters("es")), Set.of()))
        .withMessageContaining(TENANT)
        .withMessageContaining("autoconfigured")
        .withMessageContaining("camundaexporter");
  }

  @Test
  void shouldFailWhenAssignedIdIsUnknown() {
    environment.setProperty("camunda.physical-tenants.tenanta.data.exporters-assigned[0]", "ghost");
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validate(tenants(TENANT, camundaWithExporters("es")), Set.of()))
        .withMessageContaining(TENANT)
        .withMessageContaining("unknown exporter id(s) [ghost]");
  }

  @Test
  void shouldFailOnBlankAssignedEntry() {
    // blank entries must be caught before the unknown-id check; otherwise they render as `[]`
    environment.setProperty("camunda.physical-tenants.tenanta.data.exporters-assigned[0]", "");
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validate(tenants(TENANT, camundaWithExporters("es")), Set.of()))
        .withMessageContaining(TENANT)
        .withMessageContaining("blank entry");
  }

  @Test
  void shouldFailWhenTenantConfiguresGenericExporterButDoesNotAssignIt() {
    // tenant declares "custom" under data.exporters (yes/no/yes) but assigns only "es"
    environment.setProperty("camunda.physical-tenants.tenanta.data.exporters-assigned[0]", "es");
    environment.setProperty(
        "camunda.physical-tenants.tenanta.data.exporters.custom.class-name", "com.acme.Custom");
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validate(tenants(TENANT, camundaWithExporters("es", "custom")), Set.of()))
        .withMessageContaining(TENANT)
        .withMessageContaining("configures exporter id(s) [custom]");
  }

  @Test
  void shouldFailWhenAssignedEmptyButTenantConfiguresGenericExporter() {
    environment.setProperty("camunda.physical-tenants.tenanta.data.exporters-assigned", "");
    environment.setProperty(
        "camunda.physical-tenants.tenanta.data.exporters.custom.class-name", "com.acme.Custom");
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validate(tenants(TENANT, camundaWithExporters("custom")), Set.of()))
        .withMessageContaining("configures exporter id(s) [custom]");
  }

  @Test
  void shouldPassForTenantPrivateExporterThatIsAssignedAndConfigured() {
    // no root entry, tenant assigns + configures it (no/yes/yes) — a tenant-private exporter
    environment.setProperty(
        "camunda.physical-tenants.tenanta.data.exporters-assigned[0]", "private");
    environment.setProperty(
        "camunda.physical-tenants.tenanta.data.exporters.private.class-name", "com.acme.Private");
    assertThatCode(() -> validate(tenants(TENANT, camundaWithExporters("private")), Set.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldValidateExplicitlyDeclaredDefaultLikeAnyOtherTenant() {
    final Map<String, Camunda> resolved =
        tenants(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, camundaWithExporters("es"));
    final Set<String> explicitlyDeclared = Set.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validate(resolved, explicitlyDeclared))
        .withMessageContaining(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
        .withMessageContaining("must declare");
  }

  @Test
  void shouldRejectRootLevelExportersAssigned() {
    environment.setProperty("camunda.data.exporters-assigned[0]", "es");
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(
            () -> PhysicalTenantExporterAssignedValidation.validateRootAssignedAbsent(environment))
        .withMessageContaining("must not be set at the root level");
  }

  @Test
  void shouldAcceptAbsentRootLevelExportersAssigned() {
    assertThatCode(
            () -> PhysicalTenantExporterAssignedValidation.validateRootAssignedAbsent(environment))
        .doesNotThrowAnyException();
  }

  // --- helpers -----------------------------------------------------------------------------------

  private void validate(final Map<String, Camunda> resolved, final Set<String> explicitlyDeclared) {
    PhysicalTenantExporterAssignedValidation.validate(environment, resolved, explicitlyDeclared);
  }

  private static Camunda camundaWithExporters(final String... exporterIds) {
    final Camunda camunda = new Camunda();
    final Map<String, Exporter> exporters = new LinkedHashMap<>();
    for (final String id : exporterIds) {
      final Exporter exporter = new Exporter();
      exporter.setClassName("com.acme." + id);
      exporters.put(id, exporter);
    }
    camunda.getData().setExporters(exporters);
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
