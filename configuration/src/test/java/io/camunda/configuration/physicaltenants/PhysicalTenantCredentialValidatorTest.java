/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Secrets.FileInternalConfig;
import io.camunda.configuration.Secrets.FileStore;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PhysicalTenantCredentialValidatorTest {

  private static final String PASSWORD = "data.secondary-storage.rdbms.password";

  @Test
  void shouldRejectALiteralCredentialOnCreateWithoutNamingIt() {
    // given
    final var validator = new PhysicalTenantCredentialValidator((id, subtree) -> new Camunda());

    // when
    final var violations =
        validator.validate(
            PhysicalTenantConfigChange.create("tenanta", Map.of(PASSWORD, "s3cr3t")));

    // then
    assertThat(violations)
        .singleElement()
        .asString()
        .contains("Physical tenant 'tenanta'", PASSWORD)
        .doesNotContain("s3cr3t");
  }

  @Test
  void shouldRejectALiteralCredentialOnUpdate() {
    // given
    final var validator = new PhysicalTenantCredentialValidator((id, subtree) -> new Camunda());

    // when
    final var violations =
        validator.validate(
            PhysicalTenantConfigChange.update(
                "tenantb", Map.of("name", "B"), Map.of("name", "B", PASSWORD, "s3cr3t")));

    // then
    assertThat(violations).singleElement().asString().contains(PASSWORD).doesNotContain("s3cr3t");
  }

  @Test
  void shouldAcceptAReferenceToAStoreTheTenantInheritsFromTheRoot() {
    // given
    final var validator =
        new PhysicalTenantCredentialValidator((id, subtree) -> withInternalConfigStore());

    // when
    final var violations =
        validator.validate(
            PhysicalTenantConfigChange.create(
                "tenantc", Map.of(PASSWORD, "camunda.secrets.tenantc-password")));

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldRejectAReferenceWhenTheTenantHasNoInternalConfigStore() {
    // given
    final var validator = new PhysicalTenantCredentialValidator((id, subtree) -> new Camunda());

    // when
    final var violations =
        validator.validate(
            PhysicalTenantConfigChange.create(
                "tenantd", Map.of(PASSWORD, "camunda.secrets.tenantd-password")));

    // then
    assertThat(violations).singleElement().asString().contains("no internal-config secret store");
  }

  @Test
  void shouldAcceptAConfigurationWithoutCredentials() {
    // given
    final var validator = new PhysicalTenantCredentialValidator((id, subtree) -> new Camunda());

    // when
    final var violations =
        validator.validate(
            PhysicalTenantConfigChange.create(
                "tenante", Map.of("name", "E", "data.secondary-storage.type", "none")));

    // then
    assertThat(violations).isEmpty();
  }

  private static Camunda withInternalConfigStore() {
    final var camunda = new Camunda();
    final var store = new FileStore();
    store.setInternalConfig(new FileInternalConfig());
    camunda.getSecrets().getStores().getFile().put("default", store);
    return camunda;
  }
}
