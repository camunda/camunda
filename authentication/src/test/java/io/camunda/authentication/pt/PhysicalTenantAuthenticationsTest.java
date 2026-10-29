/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

final class PhysicalTenantAuthenticationsTest {

  @Test
  void shouldServeTheStartupTenantsAndDefault() {
    // given
    final var environment = new MockEnvironment();
    environment.setProperty(
        "camunda.physical-tenants.tenanta.security.authentication.oidc.client-id", "client");

    // when
    final var authentications = PhysicalTenantAuthentications.of(environment);

    // then
    assertThat(authentications.physicalTenantIds()).containsExactlyInAnyOrder("default", "tenanta");
  }

  @Test
  void shouldKeepADerivedValueWhileTheConfigurationIsUnchanged() {
    // given
    final var authentications = new PhysicalTenantAuthentications(Map.of());
    final var builds = new AtomicInteger();
    final var derived = authentications.derive((id, config) -> builds.incrementAndGet());
    authentications.put("tenanta", new AuthenticationConfiguration());

    // when
    derived.get("tenanta");
    derived.get("tenanta");

    // then
    assertThat(builds).hasValue(1);
  }

  @Test
  void shouldBuildADerivedValueAgainFromAChangedConfiguration() {
    // given
    final var authentications = new PhysicalTenantAuthentications(Map.of());
    final var derived = authentications.derive((id, config) -> config);
    final var before = new AuthenticationConfiguration();
    final var after = new AuthenticationConfiguration();
    authentications.put("tenanta", before);
    assertThat(derived.get("tenanta")).containsSame(before);

    // when
    authentications.put("tenanta", after);

    // then
    assertThat(derived.get("tenanta")).containsSame(after);
  }

  @Test
  void shouldReleaseTheDerivedValueOfARemovedTenant() {
    // given
    final var authentications = new PhysicalTenantAuthentications(Map.of());
    final var derived = authentications.derive((id, config) -> id);
    authentications.put("tenanta", new AuthenticationConfiguration());
    derived.get("tenanta");

    // when
    authentications.remove("tenanta");

    // then
    assertThat(derived.heldFor()).isEmpty();
    assertThat(derived.get("tenanta")).isEmpty();
    assertThat(authentications.find("tenanta")).isEmpty();
  }
}
