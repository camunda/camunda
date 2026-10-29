/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Sends a physical tenant configuration that holds a literal password through the management API
 * and through the static configuration file, and checks that the password reaches neither the
 * broker's data directory (where the tenant configurations and the cluster configuration are kept),
 * nor the cluster configuration, nor the logs, nor the API response.
 */
@ZeebeIntegration
final class PhysicalTenantCredentialGuardIT {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String PASSWORD_KEY = "data.secondary-storage.rdbms.password";
  private static final Duration TIMEOUT = Duration.ofMinutes(1);

  private static final String FILE_TENANT = "filelit";
  private static final String LAST_FILE_TENANT = "zzclean";
  private static final String FILE_PASSWORD = "file-" + UUID.randomUUID();

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(FILE_TENANT, Storage.none())
          .withTenant(LAST_FILE_TENANT, Storage.none())
          .build();

  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker broker =
      TENANTS
          .configure(new TestStandaloneBroker().withUnauthenticatedAccess())
          .withProperty(
              "camunda.physical-tenants." + FILE_TENANT + "." + PASSWORD_KEY, FILE_PASSWORD);

  private final ListAppender logs = new ListAppender("credential-guard-" + UUID.randomUUID());

  // Spring Boot replaces the logging configuration when the first application of the JVM starts
  private final PropertyChangeListener reattachLogs =
      event -> {
        if (LoggerContext.PROPERTY_CONFIG.equals(event.getPropertyName())) {
          ((Configuration) event.getNewValue()).getRootLogger().addAppender(logs, null, null);
        }
      };

  @BeforeEach
  void captureLogs() {
    logs.start();
    final var context = (LoggerContext) LogManager.getContext(false);
    context.addPropertyChangeListener(reattachLogs);
    context.getConfiguration().getRootLogger().addAppender(logs, null, null);
    context.updateLoggers();
    broker.start().awaitCompleteTopology();
  }

  @AfterEach
  void stopCapturingLogs() {
    final var context = (LoggerContext) LogManager.getContext(false);
    context.removePropertyChangeListener(reattachLogs);
    context.getConfiguration().getRootLogger().removeAppender(logs.getName());
    context.updateLoggers();
    logs.stop();
  }

  @Test
  void shouldRejectALiteralPasswordOnCreateBeforeAnythingIsStored() {
    // given
    final var tenantId = uniqueTenantId();
    final var password = "api-" + UUID.randomUUID();
    final var configuration = tenantConfiguration();
    configuration.put(PASSWORD_KEY, password);

    // when
    final var response = send("POST", tenantId, configuration);

    // then
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains(PASSWORD_KEY).doesNotContain(password);
    assertThat(send("GET", tenantId, null).statusCode()).isEqualTo(404);
    assertThat(tenantConfigDirectory().resolve(tenantId)).doesNotExist();
    assertNowhere(password);
  }

  @Test
  void shouldRejectALiteralPasswordOnUpdateBeforeAnythingIsStored() {
    // given
    final var tenantId = uniqueTenantId();
    assertThat(send("POST", tenantId, tenantConfiguration()).statusCode()).isEqualTo(202);
    awaitRolledOut(tenantId);
    final var password = "api-" + UUID.randomUUID();
    final var configuration = tenantConfiguration();
    configuration.put(PASSWORD_KEY, password);

    // when
    final var response = send("PUT", tenantId, configuration);

    // then
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains(PASSWORD_KEY).doesNotContain(password);
    assertThat(versionOf(tenantId)).isEqualTo(1);
    assertNowhere(password);
  }

  @Test
  void shouldNotRegisterAFileTenantWithALiteralPassword() {
    // given
    Awaitility.await("the last file tenant is registered")
        .atMost(TIMEOUT)
        .until(() -> send("GET", LAST_FILE_TENANT, null).statusCode() == 200);

    // when
    final var response = send("GET", FILE_TENANT, null);

    // then
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(tenantConfigDirectory().resolve(FILE_TENANT)).doesNotExist();
    assertThat(
            logs.getEvents().stream()
                .map(event -> event.getMessage().getFormattedMessage())
                .filter(message -> message.contains(FILE_TENANT))
                .filter(message -> message.contains("was rejected")))
        .singleElement()
        .asString()
        .contains(PASSWORD_KEY);
    assertNowhere(FILE_PASSWORD);
  }

  private void assertNowhere(final String password) {
    assertThat(filesContaining(dataDirectory(), password)).isEmpty();
    assertThat(clusterConfiguration()).doesNotContain(password);
    assertThat(send("GET", "", null).body()).doesNotContain(password);
    assertThat(renderedLogs()).isNotEmpty().noneMatch(line -> line.contains(password));
  }

  private String clusterConfiguration() {
    final var configuration =
        broker
            .bean(Broker.class)
            .getBrokerContext()
            .getClusterConfigurationService()
            .getCurrentClusterConfiguration();
    try (final var http = HttpClient.newHttpClient()) {
      final var actuator =
          http.send(
                  HttpRequest.newBuilder(broker.actuatorUri("cluster")).GET().build(),
                  BodyHandlers.ofString())
              .body();
      return configuration + "\n" + actuator;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private List<String> renderedLogs() {
    return logs.getEvents().stream()
        .map(
            event -> {
              final var rendered = new StringWriter();
              rendered.append(event.getMessage().getFormattedMessage());
              if (event.getThrown() != null) {
                event.getThrown().printStackTrace(new PrintWriter(rendered));
              }
              return rendered.toString();
            })
        .toList();
  }

  private static List<Path> filesContaining(final Path root, final String text) {
    final var needle = text.getBytes(StandardCharsets.UTF_8);
    try (final Stream<Path> files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(
              file -> {
                try {
                  return indexOf(Files.readAllBytes(file), needle) >= 0;
                } catch (final IOException e) {
                  // a segment or SST file may be replaced while the walk reads it
                  return false;
                }
              })
          .toList();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static int indexOf(final byte[] haystack, final byte[] needle) {
    outer:
    for (int i = 0; i <= haystack.length - needle.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  private Path dataDirectory() {
    return Path.of(
        broker
            .bean(Broker.class)
            .getSystemContext()
            .getBrokerConfiguration()
            .getData()
            .getDirectory());
  }

  private Path tenantConfigDirectory() {
    return dataDirectory().resolve("physical-tenant-configs");
  }

  private void awaitRolledOut(final String tenantId) {
    Awaitility.await("physical tenant '%s' rolled out".formatted(tenantId))
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var status = JSON.readTree(send("GET", tenantId, null).body());
              assertThat(status.get("rolledOut").asBoolean()).isTrue();
              assertThat(status.get("changing").asBoolean()).isFalse();
            });
  }

  private long versionOf(final String tenantId) {
    try {
      return JSON.readTree(send("GET", tenantId, null).body()).get("version").asLong();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Map<String, String> tenantConfiguration() {
    final Map<String, String> configuration = new HashMap<>();
    configuration.put("data.secondary-storage.type", "none");
    configuration.put("security.initialization.default-roles.admin.users[0]", "admin");
    if (broker
        .unifiedConfig()
        .getData()
        .getExporters()
        .containsKey(TestStandaloneBroker.RECORDING_EXPORTER_ID)) {
      configuration.put("data.exporters-assigned[0]", TestStandaloneBroker.RECORDING_EXPORTER_ID);
    }
    return configuration;
  }

  private HttpResponse<String> send(
      final String method, final String tenantId, final Map<String, String> body) {
    try (final var http = HttpClient.newHttpClient()) {
      final var uri =
          tenantId.isEmpty()
              ? broker.actuatorUri("physicalTenants")
              : broker.actuatorUri("physicalTenants", tenantId);
      final var request =
          HttpRequest.newBuilder(uri)
              .header("Content-Type", "application/json")
              .method(
                  method,
                  body == null
                      ? BodyPublishers.noBody()
                      : BodyPublishers.ofString(JSON.writeValueAsString(body)))
              .build();
      return http.send(request, BodyHandlers.ofString());
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String uniqueTenantId() {
    return "cg" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }
}
