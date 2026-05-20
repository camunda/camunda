/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.pt;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class PtPocLocalIdpRunner {

  private static final int DEFAULT_REALM_HOST_PORT = 8081;
  private static final int TENANTA_REALM_HOST_PORT = 8082;
  private static final int KEYCLOAK_INTERNAL_PORT = 8080;

  private PtPocLocalIdpRunner() {}

  public static void main(final String[] args) throws Exception {
    final KeycloakContainer defaultRealm =
        DefaultTestContainers.createDefaultKeycloak()
            .withRealmImportFile("pt-poc/keycloak-default-realm.json");
    defaultRealm.setPortBindings(List.of(DEFAULT_REALM_HOST_PORT + ":" + KEYCLOAK_INTERNAL_PORT));

    final KeycloakContainer tenantaRealm =
        DefaultTestContainers.createDefaultKeycloak()
            .withRealmImportFile("pt-poc/keycloak-tenanta-realm.json");
    tenantaRealm.setPortBindings(List.of(TENANTA_REALM_HOST_PORT + ":" + KEYCLOAK_INTERNAL_PORT));

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  defaultRealm.stop();
                  tenantaRealm.stop();
                }));

    defaultRealm.start();
    tenantaRealm.start();

    System.out.println();
    System.out.println("=== PT-PoC local IdPs ready ===");
    System.out.println(
        "default issuer:   http://localhost:" + DEFAULT_REALM_HOST_PORT + "/realms/default");
    System.out.println(
        "tenanta issuer:   http://localhost:" + TENANTA_REALM_HOST_PORT + "/realms/tenanta");
    System.out.println();
    System.out.println("default client:   camunda-pt-default-client / default-secret");
    System.out.println("tenanta client:   camunda-pt-tenanta-client / tenanta-secret");
    System.out.println();
    System.out.println("default user:     alice / alice");
    System.out.println("tenanta user:     bob / bob");
    System.out.println();
    System.out.println("Press <enter> to stop.");

    try (final BufferedReader in =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      in.readLine();
    }
  }
}
