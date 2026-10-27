/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * The arguments as Spring actually binds them from a command line.
 *
 * <p>{@link RestoreArgumentsTest} drives the setters directly, which cannot see binding failures —
 * and the valueless {@code --allTenants} is exactly such a failure: Spring gives an option with no
 * {@code =} the empty string, which no boolean conversion accepts.
 */
final class RestoreArgumentsBindingTest {

  @Test
  void shouldBindTheValuelessAllTenantsFlag() {
    // given / when
    final var arguments = bind("--allTenants");

    // then
    assertThat(arguments.allTenantsRequested()).isTrue();
  }

  @Test
  void shouldBindAnExplicitAllTenantsValue() {
    assertThat(bind("--allTenants=true").allTenantsRequested()).isTrue();
    assertThat(bind("--allTenants=false").allTenantsRequested()).isFalse();
  }

  @Test
  void shouldLeaveAllTenantsOffWhenNotPassed() {
    assertThat(bind("--backupId=27").allTenantsRequested()).isFalse();
  }

  @Test
  void shouldBindTheTenantIdInEitherSpelling() {
    assertThat(bind("--tenantId=tenanta").getTenantId()).isEqualTo("tenanta");
    assertThat(bind("--tenant-id=tenanta").getTenantId()).isEqualTo("tenanta");
  }

  @Test
  void shouldBindTheLongStandingClusterWideFlags() {
    // given / when — the spellings that already worked must keep working
    final var arguments = bind("--backupId=27,28", "--from=2026-01-01T10:00:00Z");

    // then
    assertThat(arguments.getBackupId()).containsExactly(27L, 28L);
    assertThat(arguments.getFrom()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
  }

  @Test
  void shouldBindPerTenantOverrides() {
    // given / when
    final var arguments = bind("--from=2026-01-01T10:00:00Z", "--override.tenanta.backupId=31,32");

    // then
    assertThat(arguments.getOverride()).containsOnlyKeys("tenanta");
    assertThat(arguments.getOverride().get("tenanta").getBackupId()).containsExactly(31L, 32L);
  }

  /**
   * Binds straight from a command line, with nothing rewriting the arguments first — which is the
   * point: the application is also started without such a rewrite (the integration tests build a
   * Spring application directly), so a flag that only binds after one is a flag that does not work.
   */
  private static RestoreArguments bind(final String... args) {
    final var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(new SimpleCommandLinePropertySource("commandLineArgs", args));
    return Binder.get(environment)
        .bind("", Bindable.of(RestoreArguments.class))
        .orElseGet(RestoreArguments::new);
  }
}
