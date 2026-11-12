/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

final class PhysicalTenantConfigFileWatcherTest {

  private static final Duration QUIET_PERIOD = Duration.ofSeconds(2);

  @TempDir private Path directory;
  private final AtomicLong clock = new AtomicLong();
  private Path file;
  private StandardEnvironment environment;

  @BeforeEach
  void bootFromTheFile() throws IOException {
    file = directory.resolve("application.yaml");
    Files.writeString(
        file,
        """
        camunda:
          data:
            snapshot-period: 1m
          physical-tenants:
            alpha:
              name: Alpha
            beta:
              name: Beta
        """);
    environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "systemOverride",
                Map.of("camunda.physical-tenants.beta.name", "Beta from a system property")));
    new YamlPropertySourceLoader()
        .load(
            "Config resource 'file [%s]' via location 'file:%s'".formatted(file, file),
            new FileSystemResource(file))
        .forEach(environment.getPropertySources()::addLast);
  }

  @Test
  void shouldFindTheFileSpringBootReadTheConfigurationFrom() {
    // when
    final var files = PhysicalTenantConfigFiles.of(environment);

    // then
    assertThat(files.files()).containsExactly(file.toAbsolutePath().normalize());
  }

  @Test
  void shouldReadTheTenantsOfTheChangedFileWithTheSamePrecedenceAsAtBoot() throws IOException {
    // given
    final var files = PhysicalTenantConfigFiles.of(environment);
    Files.writeString(
        file,
        """
        camunda:
          data:
            snapshot-period: 9m
          physical-tenants:
            default:
              data:
                snapshot-period: 7m
            beta:
              name: Beta changed
            gamma:
              name: Gamma
        """);

    // when
    final var subtrees = files.tenantSubtrees();

    // then
    assertThat(subtrees)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "default", Map.of("data.snapshot-period", "7m"),
                "beta", Map.of("name", "Beta from a system property"),
                "gamma", Map.of("name", "Gamma")));
  }

  @Test
  void shouldAlwaysDeclareTheDefaultTenant() throws IOException {
    // when
    final var subtrees = PhysicalTenantConfigFiles.of(environment).tenantSubtrees();

    // then
    assertThat(subtrees).containsEntry("default", Map.of()).containsKeys("alpha", "beta");
  }

  @Test
  void shouldHandOutAChangeOnceItStayedTheSameForTheQuietPeriod() throws IOException {
    // given
    final var watcher = watcher();
    write("alpha", "Alpha changed");
    assertThat(watcher.poll()).isEmpty();

    // when
    clock.addAndGet(QUIET_PERIOD.toNanos() - 1);
    final var tooEarly = watcher.poll();
    clock.addAndGet(1);
    final var stable = watcher.poll();

    // then
    assertThat(tooEarly).isEmpty();
    assertThat(stable)
        .hasValueSatisfying(
            subtrees -> assertThat(subtrees.get("alpha")).containsEntry("name", "Alpha changed"));
    assertThat(watcher.poll()).isEmpty();
  }

  @Test
  void shouldHandOutOnlyTheFinalContentsOfAFileWrittenInSteps() throws IOException {
    // given
    final var watcher = watcher();
    Files.writeString(
        file,
        """
        camunda:
          physical-tenants:
            alpha:
              name: Alpha
        """);
    watcher.poll();
    clock.addAndGet(QUIET_PERIOD.toNanos() / 2);

    // when
    write("alpha", "Alpha changed");
    watcher.poll();
    clock.addAndGet(QUIET_PERIOD.toNanos() / 2);
    final var beforeTheQuietPeriodOfTheLastWrite = watcher.poll();
    clock.addAndGet(QUIET_PERIOD.toNanos());
    final var stable = watcher.poll();

    // then
    assertThat(beforeTheQuietPeriodOfTheLastWrite).isEmpty();
    assertThat(stable)
        .hasValueSatisfying(subtrees -> assertThat(subtrees).containsKeys("alpha", "beta"));
  }

  @Test
  void shouldNotHandOutAFileWrittenAgainWithTheSameContents() throws IOException {
    // given
    final var watcher = watcher();
    final var contents = Files.readString(file);

    // when
    Files.writeString(file, contents);
    watcher.poll();
    clock.addAndGet(QUIET_PERIOD.toNanos());

    // then
    assertThat(watcher.poll()).isEmpty();
  }

  @Test
  void shouldSkipContentsThatDoNotParseUntilTheFileChangesAgain() throws IOException {
    // given
    final var watcher = watcher();
    Files.writeString(file, "camunda:\n  physical-tenants: [unclosed\n");
    watcher.poll();
    clock.addAndGet(QUIET_PERIOD.toNanos());
    final var broken = watcher.poll();
    clock.addAndGet(QUIET_PERIOD.toNanos());
    final var brokenAgain = watcher.poll();

    // when
    write("alpha", "Alpha fixed");
    watcher.poll();
    clock.addAndGet(QUIET_PERIOD.toNanos());
    final var fixed = watcher.poll();

    // then
    assertThat(broken).isEmpty();
    assertThat(brokenAgain).isEmpty();
    assertThat(fixed)
        .hasValueSatisfying(
            subtrees -> assertThat(subtrees.get("alpha")).containsEntry("name", "Alpha fixed"));
  }

  @Test
  void shouldWaitForAMissingFileInsteadOfTakingItAsAFileWithoutTenants() throws IOException {
    // given
    final var watcher = watcher();
    Files.delete(file);
    watcher.poll();
    clock.addAndGet(QUIET_PERIOD.toNanos());
    final var missing = watcher.poll();

    // when
    write("alpha", "Alpha back");
    watcher.poll();
    clock.addAndGet(QUIET_PERIOD.toNanos());
    final var back = watcher.poll();

    // then
    assertThat(missing).isEmpty();
    assertThat(back)
        .hasValueSatisfying(
            subtrees -> assertThat(subtrees.get("alpha")).containsEntry("name", "Alpha back"));
  }

  private PhysicalTenantConfigFileWatcher watcher() throws IOException {
    final var files = PhysicalTenantConfigFiles.of(environment);
    return new PhysicalTenantConfigFileWatcher(
        files, QUIET_PERIOD, files.fingerprint(), clock::get);
  }

  private void write(final String tenantId, final String name) throws IOException {
    Files.writeString(
        file,
        """
        camunda:
          physical-tenants:
            %s:
              name: %s
            beta:
              name: Beta
        """
            .formatted(tenantId, name));
  }
}
