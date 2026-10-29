/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.secrets.PhysicalTenantSecretStores.Owned;
import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.cluster.PhysicalTenant;
import io.camunda.cluster.PhysicalTenantHealth;
import io.camunda.cluster.PhysicalTenantHealth.State;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

final class InternalConfigRefresherTest {

  private static final String DEFAULT = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
  private static final Map<String, String> CONFIGURED =
      Map.of("password", "camunda.secrets.db-password");

  @TempDir private Path root;
  @AutoClose private InternalConfigSecrets secrets;

  private Path internal;
  private DefaultPhysicalTenantRegistry registry;
  private InternalConfigRefresher refresher;

  @BeforeEach
  void setUp() throws IOException {
    final var jobs = Files.createDirectory(root.resolve("jobs"));
    internal = Files.createDirectory(root.resolve("internal"));
    Files.writeString(internal.resolve("db-password"), "old");
    secrets =
        new SecretStoreConfiguration()
            .internalConfigSecrets(
                resolverFor(
                    Map.of(
                        "camunda.secrets.stores.file.default.path", jobs.toString(),
                        "camunda.secrets.stores.file.default.internal-config.path",
                            internal.toString())));
    registry =
        new DefaultPhysicalTenantRegistry(List.of(PhysicalTenant.newIncarnation(DEFAULT, null)));
    registry.reportHealth(DEFAULT, PhysicalTenantHealth.of(State.HEALTHY));
    refresher = new InternalConfigRefresher(secrets.forPhysicalTenant(DEFAULT), registry);
  }

  @Test
  void shouldPushARotatedValueToTheConsumer() throws IOException {
    // given
    final var consumer = new RecordingConsumer();
    refresher.register(consumer, Map.of("password", "old"));
    Files.writeString(internal.resolve("db-password"), "new");

    // when
    refresher.refresh();

    // then
    assertThat(consumer.applied).containsExactly(Map.of("password", "new"));
    assertThat(healthOf(DEFAULT).state()).isEqualTo(State.HEALTHY);
  }

  @Test
  void shouldNotPushAnUnchangedValue() {
    // given
    final var consumer = new RecordingConsumer();
    refresher.register(consumer, Map.of("password", "old"));

    // when
    refresher.refresh();

    // then
    assertThat(consumer.applied).isEmpty();
  }

  @Test
  void shouldReportTheTenantDegradedAndRetryWhenTheConsumerFailsToApply() throws IOException {
    // given
    final var consumer = new RecordingConsumer();
    consumer.fail = true;
    refresher.register(consumer, Map.of("password", "old"));
    Files.writeString(internal.resolve("db-password"), "new");

    // when
    refresher.refresh();

    // then
    assertThat(healthOf(DEFAULT).state()).isEqualTo(State.DEGRADED);
    assertThat(healthOf(DEFAULT).detail()).contains("recording", "cannot connect");
    assertThat(healthOf(DEFAULT).detail()).doesNotContain("new");

    // when
    consumer.fail = false;
    refresher.refresh();

    // then
    assertThat(consumer.applied).hasSize(2).last().isEqualTo(Map.of("password", "new"));
    assertThat(healthOf(DEFAULT).state()).isEqualTo(State.HEALTHY);
  }

  @Test
  void shouldLeaveATenantThatIsNotServingToItsLifecycle() throws IOException {
    // given
    final var consumer = new RecordingConsumer();
    consumer.fail = true;
    refresher.register(consumer, Map.of("password", "old"));
    Files.writeString(internal.resolve("db-password"), "new");
    final var restarting = new PhysicalTenantHealth(State.RESTARTING, "applying a change");
    registry.reportHealth(DEFAULT, restarting);

    // when
    refresher.refresh();

    // then
    assertThat(healthOf(DEFAULT)).isEqualTo(restarting);
  }

  @Test
  void shouldNotClearADegradedHealthThatAnotherComponentReported() throws IOException {
    // given
    final var consumer = new RecordingConsumer();
    refresher.register(consumer, Map.of("password", "old"));
    final var degraded = new PhysicalTenantHealth(State.DEGRADED, "config version 3 not applied");
    registry.reportHealth(DEFAULT, degraded);

    // when
    refresher.refresh();

    // then
    assertThat(healthOf(DEFAULT)).isEqualTo(degraded);
  }

  @Test
  void shouldReportTheTenantDegradedWhenTheSecretDisappears() throws IOException {
    // given
    final var consumer = new RecordingConsumer();
    refresher.register(consumer, Map.of("password", "old"));
    Files.delete(internal.resolve("db-password"));

    // when
    refresher.refresh();

    // then
    assertThat(consumer.applied).isEmpty();
    assertThat(healthOf(DEFAULT).state()).isEqualTo(State.DEGRADED);
  }

  @Test
  void shouldNotRegisterAConsumerWithoutReferences() throws IOException {
    // given
    final var consumer = new RecordingConsumer(Map.of("password", "literal"));
    refresher.register(consumer, Map.of("password", "literal"));

    // when
    refresher.refresh();

    // then
    assertThat(consumer.applied).isEmpty();
    assertThat(healthOf(DEFAULT)).isEqualTo(PhysicalTenantHealth.of(State.HEALTHY));
  }

  @Test
  void shouldRefreshEachTenantAtItsInterval() throws IOException {
    // given
    final var consumer = new RecordingConsumer();
    try (final var refreshers =
        InternalConfigRefreshers.of(secrets, Map.of(DEFAULT, Duration.ofMillis(50)), registry)) {
      refreshers
          .forPhysicalTenant(DEFAULT)
          .orElseThrow()
          .register(consumer, Map.of("password", "old"));

      // when
      Files.writeString(internal.resolve("db-password"), "new");

      // then
      Awaitility.await("the rotated value is pushed")
          .untilAsserted(() -> assertThat(consumer.applied).contains(Map.of("password", "new")));
    }
  }

  @Test
  void shouldStartRefreshingATenantAddedAtRuntime() throws IOException {
    // given
    final var addedStore = Files.createDirectory(root.resolve("added"));
    Files.writeString(addedStore.resolve("db-password"), "old");
    final var consumer = new RecordingConsumer();
    try (final var refreshers = InternalConfigRefreshers.of(secrets, Map.of(), registry)) {

      // when
      refreshers.start("added", internalConfigStore("added", addedStore), Duration.ofMillis(50));
      refreshers
          .forPhysicalTenant("added")
          .orElseThrow()
          .register(consumer, Map.of("password", "old"));
      Files.writeString(addedStore.resolve("db-password"), "new");

      // then
      Awaitility.await("the rotated value is pushed")
          .untilAsserted(() -> assertThat(consumer.applied).contains(Map.of("password", "new")));
    }
  }

  @Test
  void shouldKeepTheConsumersAndReadTheNewStoreOnceReplaced() throws IOException {
    // given
    final var firstStore = Files.createDirectory(root.resolve("first"));
    final var secondStore = Files.createDirectory(root.resolve("second"));
    Files.writeString(firstStore.resolve("db-password"), "old");
    Files.writeString(secondStore.resolve("db-password"), "old");
    final var consumer = new RecordingConsumer();
    final var released = new AtomicBoolean();
    try (final var refreshers = InternalConfigRefreshers.of(secrets, Map.of(), registry)) {
      final var first = internalConfigStore("replaced", firstStore);
      refreshers.start(
          "replaced", new Owned<>(first.value(), () -> released.set(true)), Duration.ofHours(1));
      final var refresher = refreshers.forPhysicalTenant("replaced").orElseThrow();
      refresher.register(consumer, Map.of("password", "old"));

      // when
      refreshers.start(
          "replaced", internalConfigStore("replaced", secondStore), Duration.ofMillis(50));
      Files.writeString(secondStore.resolve("db-password"), "second");

      // then
      assertThat(released).isTrue();
      assertThat(refreshers.forPhysicalTenant("replaced")).containsSame(refresher);
      Awaitility.await("the value of the new store is pushed")
          .untilAsserted(() -> assertThat(consumer.applied).contains(Map.of("password", "second")));
    }
  }

  @Test
  void shouldStopRefreshingAndCloseTheConsumersOfARemovedTenant() throws IOException {
    // given
    final var removedStore = Files.createDirectory(root.resolve("removed"));
    Files.writeString(removedStore.resolve("db-password"), "old");
    final var consumer = new RecordingConsumer();
    final var released = new AtomicBoolean();
    try (final var refreshers = InternalConfigRefreshers.of(secrets, Map.of(), registry)) {
      final var store = internalConfigStore("removed", removedStore);
      refreshers.start(
          "removed", new Owned<>(store.value(), () -> released.set(true)), Duration.ofMillis(20));
      refreshers
          .forPhysicalTenant("removed")
          .orElseThrow()
          .register(consumer, Map.of("password", "old"));

      // when
      refreshers.stop("removed");
      Files.writeString(removedStore.resolve("db-password"), "new");

      // then
      assertThat(consumer.closed).isTrue();
      assertThat(released).isTrue();
      assertThat(refreshers.forPhysicalTenant("removed")).isEmpty();
      Awaitility.await("no refresh runs after the tenant was removed")
          .during(Duration.ofMillis(200))
          .atMost(Duration.ofSeconds(2))
          .until(() -> consumer.applied.isEmpty());
    }
  }

  private Owned<InternalConfigSecretResolver> internalConfigStore(
      final String physicalTenantId, final Path internalConfigPath) throws IOException {
    final var jobs = Files.createDirectories(root.resolve(physicalTenantId + "-jobs"));
    final var configuration =
        resolverFor(
                Map.of(
                    "camunda.secrets.stores.file.default.path", jobs.toString(),
                    "camunda.secrets.stores.file.default.internal-config.path",
                        internalConfigPath.toString()))
            .forPhysicalTenant(DEFAULT);
    return new PhysicalTenantSecretStores(InstantSource.system(), new SimpleMeterRegistry())
        .internalConfig(physicalTenantId, configuration.getSecrets());
  }

  private static PhysicalTenantResolver resolverFor(final Map<String, Object> properties) {
    final var env = new MockEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("test", properties));
    final var camunda = new Camunda();
    Binder.get(env).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return PhysicalTenantResolver.of(env, camunda);
  }

  private static final class RecordingConsumer implements InternalConfigRefresher.Consumer {

    private final Map<String, String> configured;
    private final List<Map<String, String>> applied = new CopyOnWriteArrayList<>();
    private volatile boolean fail;
    private volatile boolean closed;

    private RecordingConsumer() {
      this(CONFIGURED);
    }

    private RecordingConsumer(final Map<String, String> configured) {
      this.configured = configured;
    }

    @Override
    public String name() {
      return "recording";
    }

    @Override
    public Map<String, String> configured() {
      return configured;
    }

    @Override
    public void apply(final Map<String, String> resolved) {
      applied.add(resolved);
      if (fail) {
        throw new IllegalStateException("cannot connect");
      }
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  private PhysicalTenantHealth healthOf(final String physicalTenantId) {
    return registry.health(physicalTenantId).orElseThrow();
  }
}
