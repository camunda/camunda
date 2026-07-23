/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

import static io.camunda.spring.utils.PhysicalTenantIdDiscovery.MAX_TENANT_ID_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantIdDiscoveryTest {

  @Test
  void shouldReturnEmptySetWhenPrefixAbsent() {
    // given
    final MockEnvironment environment = new MockEnvironment();

    // when
    final Set<String> tenantIds = PhysicalTenantIdDiscovery.discover(environment);

    // then
    assertThat(tenantIds).isEmpty();
  }

  @Test
  void shouldDiscoverTenantIdsAcrossMultiplePropertySources() {
    // given two separate property sources, each declaring a different tenant
    final MockEnvironment environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "first", Map.of("camunda.physical-tenants.tenanta.cluster.size", 4)));
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "second", Map.of("camunda.physical-tenants.tenantb.cluster.size", 8)));

    // when
    final Set<String> tenantIds = PhysicalTenantIdDiscovery.discover(environment);

    // then
    assertThat(tenantIds).containsExactlyInAnyOrder("tenanta", "tenantb");
  }

  @Test
  void shouldRejectInvalidTenantIds() {
    // tenant ids must be lowercase alphanumeric — no underscores, no uppercase, no dashes
    // (dashes would make yaml and env-var forms address two different tenants).
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantIdDiscovery.validateTenantId("Tenant_A"))
        .withMessageContaining("Invalid physical tenant id");
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantIdDiscovery.validateTenantId("-leading-dash"))
        .withMessageContaining("Invalid physical tenant id");
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantIdDiscovery.validateTenantId("tenant-a"))
        .withMessageContaining("Invalid physical tenant id");
  }

  @Test
  void shouldRejectTenantIdExceeding64Characters() {
    // given a tenant id that is exactly one character over the limit
    final String tooLong = "a".repeat(MAX_TENANT_ID_LENGTH + 1);

    // when / then
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantIdDiscovery.validateTenantId(tooLong))
        .withMessageContaining("Invalid physical tenant id")
        .withMessageContaining("must not exceed " + MAX_TENANT_ID_LENGTH);
  }

  @Test
  void shouldAcceptTenantIdOfExactly64Characters() {
    // given a tenant id at exactly the maximum allowed length — must not throw
    final String maxLength = "a".repeat(MAX_TENANT_ID_LENGTH);

    // when / then no exception
    PhysicalTenantIdDiscovery.validateTenantId(maxLength);
  }

  @Test
  void shouldRejectDiscoveredTenantIdThatFailsValidation() {
    // given a property source declaring a tenant id over the 64-character limit
    final MockEnvironment environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.physical-tenants."
                        + "a".repeat(MAX_TENANT_ID_LENGTH + 1)
                        + ".cluster.size",
                    4)));

    // when / then
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantIdDiscovery.discover(environment))
        .withMessageContaining("Invalid physical tenant id");
  }
}
