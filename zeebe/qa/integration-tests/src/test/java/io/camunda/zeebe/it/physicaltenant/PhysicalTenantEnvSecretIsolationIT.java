/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Secrets.EnvStore;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;

/**
 * Backs each physical tenant's secret store with the environment variables carrying its own prefix.
 *
 * <p>The broker runs inside this JVM, which cannot set its own environment variables, so the build
 * sets the {@code PT_ENV_SECRET_IT_*} variables on the forked test JVM; see the failsafe
 * configuration in this module's {@code pom.xml} and the {@code it} task in its {@code
 * build.gradle.kts}.
 */
final class PhysicalTenantEnvSecretIsolationIT extends AbstractPhysicalTenantSecretIsolationIT {

  // must match the variables the build sets on the test JVM
  private static final Map<String, String> PREFIX_BY_TENANT =
      Map.of(TENANT_A, "PT_ENV_SECRET_IT_TENANTA_", TENANT_B, "PT_ENV_SECRET_IT_TENANTB_");

  @BeforeAll
  static void requireTheVariablesTheBuildSets() {
    // run outside the build, e.g. from an IDE, the stores would be empty and the job test would
    // only fail after its long poll, without saying why
    SECRETS_BY_TENANT.forEach(
        (tenantId, secrets) ->
            secrets.forEach(
                (name, value) -> requireVariable(PREFIX_BY_TENANT.get(tenantId) + name, value)));
  }

  // asserts on the one variable, never the whole environment, which a failure would print
  private static void requireVariable(final String name, final String expectedValue) {
    assertThat(System.getenv(name))
        .as(
            "%s, set by the failsafe <environmentVariables> in pom.xml and the 'it' task in"
                + " build.gradle.kts",
            name)
        .isEqualTo(expectedValue);
  }

  @Override
  protected void configureStore(final String tenantId, final Camunda tenantConfig) {
    final var store = new EnvStore();
    store.setPrefix(PREFIX_BY_TENANT.get(tenantId));
    tenantConfig.getSecrets().getStores().getEnv().put("default", store);
  }
}
