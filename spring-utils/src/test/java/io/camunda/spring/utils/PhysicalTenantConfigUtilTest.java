/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

import static io.camunda.spring.utils.PhysicalTenantConfigUtil.MAX_TENANT_ID_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantConfigUtilTest {

  @Test
  void shouldReturnEmptySetWhenPrefixAbsent() {
    // given
    final MockEnvironment environment = new MockEnvironment();

    // when
    final Set<String> tenantIds = PhysicalTenantConfigUtil.discover(environment);

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
    final Set<String> tenantIds = PhysicalTenantConfigUtil.discover(environment);

    // then
    assertThat(tenantIds).containsExactlyInAnyOrder("tenanta", "tenantb");
  }

  @Test
  void shouldRejectInvalidTenantIds() {
    // tenant ids must be lowercase alphanumeric — no underscores, no uppercase, no dashes
    // (dashes would make yaml and env-var forms address two different tenants).
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantConfigUtil.validateTenantId("Tenant_A"))
        .withMessageContaining("Invalid physical tenant id");
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantConfigUtil.validateTenantId("-leading-dash"))
        .withMessageContaining("Invalid physical tenant id");
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantConfigUtil.validateTenantId("tenant-a"))
        .withMessageContaining("Invalid physical tenant id");
  }

  @Test
  void shouldRejectNullTenantIdWithoutNpe() {
    // given a null tenant id — must fail with the documented exception, not a NullPointerException
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantConfigUtil.validateTenantId(null))
        .withMessageContaining("Invalid physical tenant id");
  }

  @Test
  void shouldRejectTenantIdExceeding64Characters() {
    // given a tenant id that is exactly one character over the limit
    final String tooLong = "a".repeat(MAX_TENANT_ID_LENGTH + 1);

    // when / then
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantConfigUtil.validateTenantId(tooLong))
        .withMessageContaining("Invalid physical tenant id")
        .withMessageContaining("must not exceed " + MAX_TENANT_ID_LENGTH)
        .withMessageNotContaining(tooLong);
  }

  @Test
  void shouldAcceptTenantIdOfExactly64Characters() {
    // given a tenant id at exactly the maximum allowed length — must not throw
    final String maxLength = "a".repeat(MAX_TENANT_ID_LENGTH);

    // when / then no exception
    PhysicalTenantConfigUtil.validateTenantId(maxLength);
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
        .isThrownBy(() -> PhysicalTenantConfigUtil.discover(environment))
        .withMessageContaining("Invalid physical tenant id");
  }

  @Test
  void shouldInvokeConsumerPerTenantPropertyWithRelativeName() {
    // given a tenant declaring two properties
    final MockEnvironment environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.physical-tenants.tenanta.cluster.size",
                    4,
                    "camunda.physical-tenants.tenanta.cluster.name",
                    "n")));

    // when
    final Map<String, List<String>> relativesByTenant = new LinkedHashMap<>();
    PhysicalTenantConfigUtil.forEachTenantProperty(
        environment,
        (tenantId, relative) ->
            relativesByTenant
                .computeIfAbsent(tenantId, k -> new ArrayList<>())
                .add(relative.toString()));

    // then the consumer receives the id and each property relative to the tenant prefix
    assertThat(relativesByTenant).containsOnlyKeys("tenanta");
    assertThat(relativesByTenant.get("tenanta"))
        .containsExactlyInAnyOrder("cluster.size", "cluster.name");
  }

  @Test
  void shouldPassEmptyRelativeNameWhenKeyIsOnlyTheTenantIdSegment() {
    // given a value bound directly to the tenant-id segment, with no property beneath it
    final MockEnvironment environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("test", Map.of("camunda.physical-tenants.tenanta", "v")));

    // when
    final Map<String, Boolean> relativeEmptyByTenant = new LinkedHashMap<>();
    PhysicalTenantConfigUtil.forEachTenantProperty(
        environment,
        (tenantId, relative) -> relativeEmptyByTenant.put(tenantId, relative.isEmpty()));

    // then the consumer fires once for the id with an empty relative name — the boundary both
    // validators rely on to skip the id-only key
    assertThat(relativeEmptyByTenant).containsOnlyKeys("tenanta");
    assertThat(relativeEmptyByTenant.get("tenanta")).isTrue();
  }

  @Test
  void shouldValidateIdsDuringForEachTenantProperty() {
    // given a tenant id over the length limit
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

    // when / then the walk validates each surfaced id and fails fast
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(
            () -> PhysicalTenantConfigUtil.forEachTenantProperty(environment, (id, relative) -> {}))
        .withMessageContaining("Invalid physical tenant id");
  }
}
