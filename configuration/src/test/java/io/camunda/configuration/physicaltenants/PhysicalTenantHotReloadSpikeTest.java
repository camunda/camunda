/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * SPIKE (not production code): proves what Spring does and does not give us for no-restart physical
 * tenants.
 *
 * <ol>
 *   <li>A file-backed {@link PropertySource} can be swapped in a live {@code Environment}, and the
 *       existing {@link PhysicalTenantResolver} re-resolves from it with full validation.
 *   <li>Spring Boot has no file watcher for config data; a {@link WatchService} + {@code
 *       ApplicationEvent} is ~30 lines.
 *   <li>Diffing two property snapshots per tenant can classify a change (secrets-only vs. needs
 *       module restart).
 *   <li>Hazard: legacy-fallback getters read a static global Environment, so previously resolved
 *       {@link Camunda} snapshots are NOT immutable once the Environment changes.
 *   <li>Hazard: an invalid reload must be rejected without touching the running resolver.
 * </ol>
 */
class PhysicalTenantHotReloadSpikeTest {

  private static final String FILE_PS = "physical-tenants-file";
  private static final String PREFIX = "camunda.physical-tenants.";

  @TempDir Path dir;
  private MockEnvironment environment;

  @BeforeEach
  void setUp() {
    environment = new MockEnvironment();
    UnifiedConfigurationHelper.setCustomEnvironment(environment);
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  // ---- minimal "reloader": what production would own ------------------------------------------

  enum ChangeScope {
    /** Only {@code secrets.*} keys changed: tear down exporter/secret consumers only. */
    SECRETS_ONLY,
    /** Anything else: restart the whole PT module. */
    FULL_MODULE
  }

  /** Loads the file as a PropertySource. Same loader Spring Boot uses for application.yaml. */
  private PropertySource<?> load(final Path file) throws IOException {
    return new YamlPropertySourceLoader().load(FILE_PS, new FileSystemResource(file)).getFirst();
  }

  /** Flattened {@code key -> value} for one tenant, from the property source. */
  private static Map<String, Object> flatten(
      final PropertySource<?> source, final String tenantId) {
    final var prefix = PREFIX + tenantId + ".";
    final var out = new java.util.TreeMap<String, Object>();
    for (final var name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
      if (name.startsWith(prefix)) {
        out.put(name.substring(prefix.length()), source.getProperty(name));
      }
    }
    return out;
  }

  private static Set<String> tenantsIn(final PropertySource<?> source) {
    final var ids = new TreeSet<String>();
    for (final var name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
      if (name.startsWith(PREFIX)) {
        ids.add(name.substring(PREFIX.length()).split("[.\\[]")[0]);
      }
    }
    return ids;
  }

  private static ChangeScope classify(
      final Map<String, Object> before, final Map<String, Object> after) {
    final var keys = new HashSet<>(before.keySet());
    keys.addAll(after.keySet());
    final boolean onlySecrets =
        keys.stream()
            .filter(k -> !java.util.Objects.equals(before.get(k), after.get(k)))
            .allMatch(k -> k.startsWith("secrets."));
    return onlySecrets ? ChangeScope.SECRETS_ONLY : ChangeScope.FULL_MODULE;
  }

  private PhysicalTenantResolver resolve() {
    final var camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return PhysicalTenantResolver.of(environment, camunda);
  }

  private static String tenantYaml(final String id, final String indexPrefix, final String secret) {
    return """
    camunda:
      physical-tenants:
        %s:
          data.secondary-storage.elasticsearch.index-prefix: %s
          secrets.stores.file.vault.path: %s
          security.initialization.default-roles.admin.users[0]: %s-admin
    """
        .formatted(id, indexPrefix, secret, id);
  }

  // ---- 1. reload + classification --------------------------------------------------------------

  @Test
  void shouldReResolveFromSwappedFileAndClassifyChanges() throws IOException {
    // given tenanta loaded from a file property source
    final var file = dir.resolve("physical-tenants.yaml");
    Files.writeString(file, tenantYaml("tenanta", "pa", "/secrets/v1"));
    var current = load(file);
    environment.getPropertySources().addFirst(current);
    final var before = resolve();
    assertThat(before.known()).containsExactlyInAnyOrder("default", "tenanta");

    // when only the secret path changes
    Files.writeString(file, tenantYaml("tenanta", "pa", "/secrets/v2"));
    var next = load(file);
    final var scopeSecret = classify(flatten(current, "tenanta"), flatten(next, "tenanta"));
    environment.getPropertySources().replace(FILE_PS, next);
    current = next;
    final var afterSecret = resolve();

    // then it is a secrets-only change and the resolved config reflects the new value
    assertThat(scopeSecret).isEqualTo(ChangeScope.SECRETS_ONLY);
    assertThat(afterSecret.forPhysicalTenant("tenanta").getSecrets().getStores().getFile())
        .containsKey("vault");

    // when the index prefix changes (storage coordinates)
    Files.writeString(file, tenantYaml("tenanta", "pa2", "/secrets/v2"));
    next = load(file);
    final var scopeStorage = classify(flatten(current, "tenanta"), flatten(next, "tenanta"));

    // then it needs a full module restart
    assertThat(scopeStorage).isEqualTo(ChangeScope.FULL_MODULE);

    // when a new tenant appears
    Files.writeString(
        file,
        tenantYaml("tenanta", "pa2", "/secrets/v2")
            + tenantYaml("tenantb", "pb", "/s")
                .substring("camunda:\n  physical-tenants:\n".length()));
    next = load(file);
    // then discovery by key inspection sees it
    assertThat(tenantsIn(next)).containsExactly("tenanta", "tenantb");
  }

  // ---- 2. watcher + event -----------------------------------------------------------------------

  @Test
  void shouldNotifyListenersWhenFileChanges() throws Exception {
    // given a watcher on the directory publishing to a Spring context
    final var file = dir.resolve("physical-tenants.yaml");
    Files.writeString(file, tenantYaml("tenanta", "pa", "/secrets/v1"));
    final var received = new CopyOnWriteArrayList<String>();
    try (final var ctx = new GenericApplicationContext();
        final var watch = dir.getFileSystem().newWatchService()) {
      ctx.addApplicationListener(e -> received.add(e.getClass().getSimpleName()));
      ctx.refresh();
      dir.register(watch, StandardWatchEventKinds.ENTRY_MODIFY);
      final var stop = new AtomicReference<>(false);
      final var thread =
          new Thread(
              () -> {
                try {
                  while (!stop.get()) {
                    final var key = watch.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (key == null) {
                      continue;
                    }
                    key.pollEvents();
                    key.reset();
                    ctx.publishEvent(new ConfigFileChanged(file));
                  }
                } catch (final InterruptedException | java.nio.file.ClosedWatchServiceException e) {
                  // stop
                }
              });
      thread.start();

      // when
      Files.writeString(file, tenantYaml("tenanta", "pa", "/secrets/v2"));

      // then Spring's event bus (plain ApplicationEvent) delivers it; no spring-cloud needed
      await().atMost(Duration.ofSeconds(5)).until(() -> received.contains("ConfigFileChanged"));
      stop.set(true);
      thread.join();
    }
  }

  static final class ConfigFileChanged extends ApplicationEvent {
    ConfigFileChanged(final Path file) {
      super(file);
    }
  }

  // ---- 3. hazard: invalid reload ----------------------------------------------------------------

  @Test
  void shouldRejectInvalidReloadAndKeepPreviousResolverUsable() throws IOException {
    // given a valid state
    final var file = dir.resolve("physical-tenants.yaml");
    Files.writeString(file, tenantYaml("tenanta", "pa", "/s"));
    environment.getPropertySources().addFirst(load(file));
    final var good = resolve();

    // when a reload introduces two tenants on the same storage
    Files.writeString(
        file,
        tenantYaml("tenanta", "same", "/s")
            + tenantYaml("tenantb", "same", "/s")
                .substring("camunda:\n  physical-tenants:\n".length()));
    environment.getPropertySources().replace(FILE_PS, load(file));

    // then resolve() throws (validation runs on reload for free) ...
    assertThatThrownBy(this::resolve).hasMessageContaining("same");
    // ... and the previous resolver instance is untouched, so the caller can keep serving it
    assertThat(good.known()).containsExactlyInAnyOrder("default", "tenanta");
  }

  // ---- 4. hazard: old snapshots are not immutable -----------------------------------------------

  @Test
  void shouldShowOldSnapshotMutatingWhenLegacyFallbackEnvironmentChanges() {
    // given a resolved tenant whose partition count comes from the LEGACY fallback
    environment.setProperty("zeebe.broker.cluster.partitionsCount", "9");
    environment.setProperty(
        PREFIX + "tenanta.data.secondary-storage.elasticsearch.index-prefix", "pa");
    environment.setProperty(
        PREFIX + "tenanta.security.initialization.default-roles.admin.users[0]", "a");
    final var oldSnapshot = resolve().forPhysicalTenant("tenanta");
    assertThat(oldSnapshot.getCluster().getPartitionCount()).isEqualTo(9);

    // when the environment changes after resolution
    environment.setProperty("zeebe.broker.cluster.partitionsCount", "12");

    // then the OLD object now reports the NEW value: getters re-read the static Environment.
    // A "diff old vs new resolved config" therefore sees no difference for legacy-backed props.
    assertThat(oldSnapshot.getCluster().getPartitionCount()).isEqualTo(12);
  }
}
