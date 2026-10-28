/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.health.contributor.Status;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

final class InternalConfigRefresherTest {

  private static final String DEFAULT = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
  private static final Map<String, String> CONFIGURED =
      Map.of("password", "camunda.secrets.db-password");

  @TempDir private Path root;
  @AutoClose private InternalConfigSecrets secrets;

  private Path internal;
  private SecretRefreshHealthIndicator health;
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
    health = new SecretRefreshHealthIndicator();
    refresher = new InternalConfigRefresher(secrets.forPhysicalTenant(DEFAULT), health);
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
    assertThat(health.health().getStatus()).isEqualTo(Status.UP);
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
    assertThat(health.health().getStatus().getCode()).isEqualTo("DEGRADED");
    assertThat(health.health().getDetails()).containsKey(DEFAULT);
    assertThat(health.health().getDetails().toString()).doesNotContain("new");

    // when
    consumer.fail = false;
    refresher.refresh();

    // then
    assertThat(consumer.applied).hasSize(2).last().isEqualTo(Map.of("password", "new"));
    assertThat(health.health().getStatus()).isEqualTo(Status.UP);
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
    assertThat(health.health().getStatus().getCode()).isEqualTo("DEGRADED");
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
    assertThat(health.health().getDetails()).isEmpty();
  }

  @Test
  void shouldRefreshEachTenantAtItsInterval() throws IOException {
    // given
    final var consumer = new RecordingConsumer();
    try (final var refreshers =
        InternalConfigRefreshers.of(secrets, Map.of(DEFAULT, Duration.ofMillis(50)), health)) {
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
  }
}
