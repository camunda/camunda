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

import io.camunda.configuration.UnifiedConfigurationException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantRdbmsPrefixValidationTest {

  private static final String ROOT_PREFIX = "camunda.data.secondary-storage.rdbms.prefix";

  private static MockEnvironment environmentWith(final Map<String, Object> properties) {
    final MockEnvironment environment = new MockEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    return environment;
  }

  @ParameterizedTest
  @ValueSource(strings = {"ta_", "Ta_", "tA_", "ta-", "TA-", "1TA_"})
  void shouldRejectRootPrefixThatIsNotAnUnquotedUpperCaseIdentifier(final String prefix) {
    // given
    final MockEnvironment environment = environmentWith(Map.of(ROOT_PREFIX, prefix));

    // when / then the offending key and value are named
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantRdbmsPrefixValidation.validate(environment, Set.of()))
        .withMessageContaining("upper-case letters, digits and underscores")
        .withMessageContaining(ROOT_PREFIX + "=" + prefix);
  }

  @ParameterizedTest
  @ValueSource(strings = {"TA_", "C8_", "_TA", "TA1_", "TA", "  TA_  "})
  void shouldAcceptUpperCasePrefix(final String prefix) {
    // given
    final MockEnvironment environment = environmentWith(Map.of(ROOT_PREFIX, prefix));

    // when / then
    assertThatCode(() -> PhysicalTenantRdbmsPrefixValidation.validate(environment, Set.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptBlankPrefix() {
    // given a declared but empty prefix, meaning no prefix at all
    final MockEnvironment environment = environmentWith(Map.of(ROOT_PREFIX, ""));

    // when / then
    assertThatCode(() -> PhysicalTenantRdbmsPrefixValidation.validate(environment, Set.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptUndeclaredPrefix() {
    // given no prefix property at all
    final MockEnvironment environment = environmentWith(Map.of("camunda.cluster.size", 3));

    // when / then
    assertThatCode(
            () -> PhysicalTenantRdbmsPrefixValidation.validate(environment, Set.of("tenanta")))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectPerPhysicalTenantPrefix() {
    // given a lower-case override on a tenant while the root prefix is valid
    final String tenantPrefix =
        "camunda.physical-tenants.tenanta.data.secondary-storage.rdbms.prefix";
    final MockEnvironment environment =
        environmentWith(Map.of(ROOT_PREFIX, "DEFAULT_", tenantPrefix, "ta_"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(
            () -> PhysicalTenantRdbmsPrefixValidation.validate(environment, Set.of("tenanta")))
        .withMessageContaining(tenantPrefix + "=ta_");
  }

  @Test
  void shouldReportEveryInvalidPrefix() {
    // given both the root and a tenant misconfigured
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                ROOT_PREFIX,
                "default_",
                "camunda.physical-tenants.tenanta.data.secondary-storage.rdbms.prefix",
                "ta_"));

    // when / then both are named in one message
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(
            () -> PhysicalTenantRdbmsPrefixValidation.validate(environment, Set.of("tenanta")))
        .withMessageContaining(ROOT_PREFIX + "=default_")
        .withMessageContaining("tenanta.data.secondary-storage.rdbms.prefix=ta_");
  }

  @Test
  void shouldNotValidateTenantsThatWereNotDiscovered() {
    // given an undiscovered tenant id, i.e. one with no keys in the environment
    final MockEnvironment environment = environmentWith(Map.of(ROOT_PREFIX, "DEFAULT_"));

    // when / then
    assertThatCode(
            () -> PhysicalTenantRdbmsPrefixValidation.validate(environment, Set.of("tenantb")))
        .doesNotThrowAnyException();
  }
}
