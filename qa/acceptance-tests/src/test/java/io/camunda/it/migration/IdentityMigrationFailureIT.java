/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static io.camunda.it.migration.IdentityMigrationTestUtil.CAMUNDA_IDENTITY_RESOURCE_SERVER;
import static io.camunda.it.migration.IdentityMigrationTestUtil.IDENTITY_CLIENT;
import static io.camunda.it.migration.IdentityMigrationTestUtil.IDENTITY_CLIENT_SECRET;
import static io.camunda.zeebe.qa.util.cluster.TestZeebePort.CLUSTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.camunda.application.commons.migration.BlockingMigrationsRunner;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneIdentityMigration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.context.ApplicationListener;

@ZeebeIntegration
public class IdentityMigrationFailureIT {

  @TestZeebe
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled().withRdbmsExporter();

  private static final String IDENTITY_URL = "identity.example.com";
  private static final String KEYCLOAK_URL = "keycloak.example.com";
  private final LogCapturer logCapturer = new LogCapturer();

  @BeforeEach
  void resetCapturer() {
    logCapturer.stop();
    logCapturer.list.clear();
  }

  @Test
  void failIfCannotJoinCluster() {
    // given
    final IdentityMigrationProperties migrationProperties = new IdentityMigrationProperties();
    // no contact points for cluster configured
    migrationProperties.getCluster().setAwaitClusterJoinMaxAttempts(1);
    migrationProperties.getManagementIdentity().setBaseUrl(IDENTITY_URL);
    migrationProperties
        .getManagementIdentity()
        .setIssuerBackendUrl(KEYCLOAK_URL + "/realms/camunda-platform/");
    migrationProperties.getManagementIdentity().setIssuerType("KEYCLOAK");
    migrationProperties.getManagementIdentity().setClientId(IDENTITY_CLIENT);
    migrationProperties.getManagementIdentity().setClientSecret(IDENTITY_CLIENT_SECRET);
    migrationProperties.getManagementIdentity().setAudience(CAMUNDA_IDENTITY_RESOURCE_SERVER);

    try (final TestStandaloneIdentityMigration testIdentityMigration =
        new TestStandaloneIdentityMigration(migrationProperties)) {
      setupLogCapturing(testIdentityMigration, BlockingMigrationsRunner.class);

      // when
      testIdentityMigration.start();

      // then
      assertThat(testIdentityMigration.getExitCode()).isEqualTo(1);
      assertThat(
              logCapturer.contains(
                  "IdentityMigrator failed with: Failed to connect to Orchestration Cluster within PT1S.",
                  Level.ERROR))
          .isTrue();
    }
  }

  @Test
  void failOnMissingConfiguration() {
    // given
    final IdentityMigrationProperties migrationProperties = new IdentityMigrationProperties();
    // connection to broker is set
    migrationProperties
        .getCluster()
        .setInitialContactPoints(List.of("localhost:" + BROKER.mappedPort(CLUSTER)));
    // but no other config, e.g. for management identity connection

    try (final TestStandaloneIdentityMigration testIdentityMigration =
        new TestStandaloneIdentityMigration(migrationProperties)) {
      setupLogCapturing(testIdentityMigration, BlockingMigrationsRunner.class);

      // when
      final Throwable thrown = catchThrowable(testIdentityMigration::start);

      // then
      assertThat(thrown)
          .isInstanceOf(ConfigurationPropertiesBindException.class)
          .cause()
          .cause()
          .hasMessageContainingAll(
              "Audience must be provided",
              "Issuer Backend URL must be provided",
              "Client Secret must be provided",
              "Client ID must be provided",
              "Base URL must be provided");
    }
  }

  /**
   * This utility method is needed to set up the log capturing after spring initialized the app
   * context, if we do it before Spring resets everything during app startup via {@link
   * LoggingApplicationListener }.
   *
   * @param testIdentityMigration test migration app instance
   * @param loggerClass clazz to capture logs from
   */
  private void setupLogCapturing(
      final TestStandaloneIdentityMigration testIdentityMigration, final Class<?> loggerClass) {
    testIdentityMigration.withAdditionalInitializer(
        applicationContext ->
            applicationContext.addApplicationListener(
                (ApplicationListener<ApplicationStartedEvent>)
                    event -> {
                      final Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
                      logCapturer.setContext(logger.getLoggerContext());
                      logCapturer.start();
                      logger.addAppender(logCapturer);
                    }));
  }

  static class LogCapturer extends ListAppender<ILoggingEvent> {

    public boolean contains(final String string, final Level level) {
      return list.stream()
          .anyMatch(event -> event.toString().contains(string) && event.getLevel().equals(level));
    }
  }
}
