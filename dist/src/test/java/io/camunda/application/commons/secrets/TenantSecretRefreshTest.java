/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.secrets.ConfigurationSecretRefreshReport.Failure;
import io.camunda.application.commons.secrets.ConfigurationSecretRefreshReport.Reason;
import io.camunda.configuration.ConfigurationSecretReference;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.camunda.secretstore.refresh.ClientRefreshException;
import io.camunda.secretstore.refresh.RefreshableClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TenantSecretRefreshTest {

  private static final Duration INTERVAL = Duration.ofMinutes(5);
  private static final String PASSWORD = "data.secondary-storage.elasticsearch.password";
  private static final String USERNAME = "data.secondary-storage.elasticsearch.username";

  private final FakeStore store = new FakeStore();
  private final FakeClient client = new FakeClient(Set.of(PASSWORD, USERNAME), Set.of());
  private final List<RefreshableClient> clients = new ArrayList<>(List.of(client));
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ConfigurationSecretRefreshReport report =
      new ConfigurationSecretRefreshReport(
          meterRegistry, InstantSource.fixed(Instant.ofEpochSecond(1_000)));

  @Test
  void shouldPassAChangedValueToTheClientThatUsesIt() {
    // given
    final var refresh = refresh();
    store.values.put("es-password", new Resolved("new-password"));

    // when
    final var next = refresh.run();

    // then
    assertThat(client.refreshes)
        .containsExactly(Map.of(PASSWORD, "new-password", USERNAME, "camunda"));
    assertThat(next).isEqualTo(INTERVAL);
  }

  @Test
  void shouldNotRefreshAClientWhoseValuesDidntChange() {
    // given
    final var refresh = refresh();

    // when
    refresh.run();

    // then
    assertThat(client.refreshes).isEmpty();
  }

  @Test
  void shouldKeepTheCurrentValueAndBackOffWhenAReadFails() {
    // given
    final var refresh = refresh();
    store.values.put("es-password", new Failed(SecretErrorCode.NOT_FOUND, "missing", null));

    // when
    final var first = refresh.run();
    final var second = refresh.run();

    // then
    assertThat(client.refreshes).isEmpty();
    assertThat(first).isEqualTo(Duration.ofSeconds(10));
    assertThat(second).isEqualTo(Duration.ofSeconds(20));
  }

  @Test
  void shouldBackOffNoLongerThanTheIntervalAndResetOnceAReadSucceeds() {
    // given
    final var refresh = refresh();
    store.unavailable = true;
    for (var i = 0; i < 10; i++) {
      refresh.run();
    }

    // when
    final var failing = refresh.run();
    store.unavailable = false;
    final var recovered = refresh.run();

    // then
    assertThat(failing).isEqualTo(INTERVAL);
    assertThat(recovered).isEqualTo(INTERVAL);
  }

  @Test
  void shouldTryAgainOnTheNextRunWhenAClientFailsToTakeTheNewValues() {
    // given
    final var refresh = refresh();
    store.values.put("es-password", new Resolved("new-password"));
    client.failures = 1;

    // when
    refresh.run();
    refresh.run();
    refresh.run();

    // then
    assertThat(client.refreshes)
        .hasSize(2)
        .allSatisfy(values -> assertThat(values).containsEntry(PASSWORD, "new-password"));
  }

  @Test
  void shouldRefreshTheOtherClientsWhenOneFailsUnexpectedly() {
    // given
    final var failing = new FakeClient(Set.of(PASSWORD), Set.of());
    failing.unexpected = true;
    clients.addFirst(failing);
    final var refresh = refresh();
    store.values.put("es-password", new Resolved("new-password"));

    // when
    refresh.run();

    // then
    assertThat(client.refreshes).hasSize(1);
  }

  @Test
  void shouldTreatAClientRegisteredAgainAsNew() {
    // given
    final var refresh = refresh();
    store.values.put("es-password", new Resolved("new-password"));
    refresh.run();
    clients.remove(client);
    refresh.run();

    // when
    clients.add(client);
    refresh.run();

    // then
    assertThat(client.refreshes).hasSize(2);
  }

  @Test
  void shouldRefreshAClientWhenAFileItReadsIsReplaced(@TempDir final Path directory)
      throws IOException {
    // given
    final var keyStore = Files.writeString(directory.resolve("keystore.p12"), "old");
    final var fileClient = new FakeClient(Set.of(USERNAME), Set.of(keyStore));
    clients.add(fileClient);
    final var refresh = refresh();
    refresh.run();

    // when
    Files.writeString(keyStore, "new");
    refresh.run();

    // then
    assertThat(fileClient.refreshes).containsExactly(Map.of(USERNAME, "camunda"));
  }

  @Test
  void shouldReportAReferenceThatCantBeReadForEachClientThatUsesIt() {
    // given
    final var refresh = refresh();
    store.values.put("es-password", new Failed(SecretErrorCode.NOT_FOUND, "missing", null));

    // when
    refresh.run();

    // then
    assertThat(report.failing())
        .containsExactly(
            Map.entry(
                "tenanta", Set.of(new Failure("secret:es-password", "fake", Reason.NOT_FOUND))));
    assertThat(
            meterRegistry
                .get(ConfigurationSecretRefreshReport.FAILING)
                .tag("physicalTenant", "tenanta")
                .tag("reference", "secret:es-password")
                .tag("client", "fake")
                .tag("reason", "not_found")
                .gauge()
                .value())
        .isEqualTo(1);
  }

  @Test
  void shouldClearAFailureOnceTheReferenceCanBeReadAgain() {
    // given
    final var refresh = refresh();
    store.unavailable = true;
    refresh.run();

    // when
    store.unavailable = false;
    refresh.run();

    // then
    assertThat(report.failing()).isEmpty();
    assertThat(meterRegistry.find(ConfigurationSecretRefreshReport.FAILING).gauges()).isEmpty();
  }

  @Test
  void shouldReportAReferenceOnlyReadOnStartupThatCantBeRead() {
    // given
    clients.clear();
    final var refresh = refresh();
    store.unavailable = true;

    // when
    refresh.run();

    // then
    assertThat(report.failing().get("tenanta"))
        .containsExactly(new Failure("secret:es-password", "startup", Reason.STORE_UNREACHABLE));
  }

  @Test
  void shouldReportAClientThatRejectsTheNewValues() {
    // given
    final var refresh = refresh();
    store.values.put("es-password", new Resolved("new-password"));
    client.failures = 1;

    // when
    refresh.run();

    // then
    assertThat(report.failing().get("tenanta"))
        .containsExactly(new Failure("secret:es-password", "fake", Reason.REJECTED_BY_CHECK));
  }

  @Test
  void shouldRecordWhenAChangeWasApplied() {
    // given
    final var refresh = refresh();
    store.values.put("es-password", new Resolved("new-password"));

    // when
    refresh.run();

    // then
    assertThat(
            meterRegistry
                .get(ConfigurationSecretRefreshReport.LAST_APPLIED)
                .tag("physicalTenant", "tenanta")
                .tag("reference", "secret:es-password")
                .gauge()
                .value())
        .isEqualTo(1_000);
  }

  private TenantSecretRefresh refresh() {
    store.values.putIfAbsent("es-password", new Resolved("old-password"));
    return new TenantSecretRefresh(
        "tenanta",
        INTERVAL,
        () -> Optional.of(store),
        Map.of(PASSWORD, new ConfigurationSecretReference("es-password")),
        Map.of(PASSWORD, "old-password", USERNAME, "camunda"),
        () -> clients,
        report);
  }

  private static final class FakeStore implements SecretStore {

    private final Map<String, SecretResolutionResult> values = new HashMap<>();
    private boolean unavailable;

    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
      if (unavailable) {
        throw new SecretStoreUnavailableException("unreachable");
      }
      final Map<String, SecretResolutionResult> results = new HashMap<>();
      names.forEach(name -> results.put(name, values.get(name)));
      return results;
    }

    @Override
    public List<String> list() {
      return List.copyOf(values.keySet());
    }
  }

  private static final class FakeClient implements RefreshableClient {

    private final Set<String> properties;
    private final Set<Path> files;
    private final List<Map<String, String>> refreshes = new ArrayList<>();
    private int failures;
    private boolean unexpected;

    private FakeClient(final Set<String> properties, final Set<Path> files) {
      this.properties = properties;
      this.files = files;
    }

    @Override
    public String name() {
      return "fake";
    }

    @Override
    public Set<String> properties() {
      return properties;
    }

    @Override
    public Set<Path> files() {
      return files;
    }

    @Override
    public void refresh(final Map<String, String> values) {
      refreshes.add(values);
      if (unexpected) {
        throw new IllegalStateException("unexpected");
      }
      if (failures > 0) {
        failures--;
        throw new ClientRefreshException("rejected", new IllegalStateException("rejected"));
      }
    }
  }
}
