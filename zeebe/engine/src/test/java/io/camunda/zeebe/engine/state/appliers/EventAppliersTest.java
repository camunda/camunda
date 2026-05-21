/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.state.EventApplier.NoSuchEventApplier;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventAppliersTest {

  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(EventAppliersTest.class);

  private EventAppliers eventAppliers;

  @Mock private TypedEventApplier<Intent, ? extends RecordValue> mockedApplier;
  @Mock private TypedEventApplier<Intent, ? extends RecordValue> anotherMockedApplier;

  @BeforeEach
  void setup() {
    eventAppliers = new EventAppliers();
  }

  @Test
  void shouldApplyStateUsingRegisteredApplier() {
    // given
    final var intent = mock(Intent.class);
    when(intent.isEvent()).thenReturn(true);
    eventAppliers.register(intent, 1, mockedApplier);

    // when
    eventAppliers.applyState(1, intent, null, 1);

    // then
    Mockito.verify(mockedApplier).applyState(anyLong(), any());
  }

  @Test
  void shouldNotApplyStateUsingUnregisteredApplier() {
    // given no registered appliers

    // then
    assertThatExceptionOfType(NoSuchEventApplier.NoApplierForIntent.class)
        .isThrownBy(() -> eventAppliers.applyState(1, Intent.UNKNOWN, null, 1));
    Mockito.verify(mockedApplier, Mockito.never()).applyState(anyLong(), any());
  }

  @Test
  void shouldNotApplyStateUsingRegisteredApplierForOlderVersion() {
    // given
    final var intent = mock(Intent.class);
    when(intent.isEvent()).thenReturn(true);

    // when
    eventAppliers.register(intent, 1, mockedApplier);

    // then
    assertThatExceptionOfType(NoSuchEventApplier.NoApplierForVersion.class)
        .isThrownBy(() -> eventAppliers.applyState(1, intent, null, 2));
    Mockito.verify(mockedApplier, Mockito.never()).applyState(anyLong(), any());
  }

  @Test
  void shouldApplyStateUsingRegisteredApplierForSpecificVersion() {
    // given
    final var intent = mock(Intent.class);
    when(intent.isEvent()).thenReturn(true);
    eventAppliers.register(intent, 1, mockedApplier);
    eventAppliers.register(intent, 2, anotherMockedApplier);

    // when
    eventAppliers.applyState(1, intent, null, 2);

    // then
    Mockito.verify(mockedApplier, Mockito.never()).applyState(anyLong(), any());
    Mockito.verify(anotherMockedApplier).applyState(anyLong(), any());
  }

  @Test
  void shouldGetLatestVersionOfOnlyRegisteredVersion() {
    // given
    final var intent = mock(Intent.class);
    when(intent.isEvent()).thenReturn(true);

    final var expectedVersion = 1;
    eventAppliers.register(intent, expectedVersion, mockedApplier);

    // when
    final var actualVersion = eventAppliers.getLatestVersion(intent);

    // then
    assertThat(actualVersion).isEqualTo(expectedVersion);
  }

  @Test
  void shouldGetLatestVersionOfTwoRegisteredVersions() {
    // given
    final var intent = mock(Intent.class);
    when(intent.isEvent()).thenReturn(true);

    final var expectedVersion = 2;
    eventAppliers.register(intent, 1, mockedApplier);
    eventAppliers.register(intent, expectedVersion, mockedApplier);

    // when
    final var actualVersion = eventAppliers.getLatestVersion(intent);

    // then
    assertThat(actualVersion).isEqualTo(expectedVersion);
  }

  @Test
  void shouldGetLatestVersionMinusOneWhenNoRegisteredVersion() {
    // given
    final var expectedVersion = -1;

    // when
    final var actualVersion = eventAppliers.getLatestVersion(Intent.UNKNOWN);

    // then
    assertThat(actualVersion).isEqualTo(expectedVersion);
  }

  @Test
  void shouldGetLatestVersionWhenMultipleRegisteredEventAppliersWithDifferentIntents() {
    // given
    final var intent = mock(Intent.class);
    when(intent.isEvent()).thenReturn(true);
    final var expectedVersion = 1;
    eventAppliers.register(intent, expectedVersion, mockedApplier);
    eventAppliers.register(ProcessIntent.CREATED, expectedVersion, mockedApplier);

    // when
    final var actualVersion = eventAppliers.getLatestVersion(intent);

    // then
    assertThat(actualVersion).isEqualTo(expectedVersion);
  }

  @Test
  void shouldRegisterApplierForAllIntents() {
    // given
    final var events =
        Intent.INTENT_CLASSES.stream()
            .flatMap(c -> Arrays.stream(c.getEnumConstants()))
            .filter(Intent::isEvent)
            // CheckpointIntent is not handled by the engine
            .filter(intent -> !(intent instanceof CheckpointIntent));

    // when
    eventAppliers.registerEventAppliers(mock(MutableProcessingState.class));

    // then
    assertThat(events)
        .allSatisfy(
            intent ->
                assertThat(eventAppliers.getLatestVersion(intent))
                    .describedAs(
                        "Intent %s.%s has a registered event applier",
                        intent.getClass().getSimpleName(), intent.name())
                    .isNotEqualTo(-1));
  }

  @Test
  void cannotRegisterNullApplier() {
    // given
    final var intent = mock(Intent.class);

    // then
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> eventAppliers.register(intent, 1, null));
  }

  @Test
  void cannotRegisterApplierForNullIntent() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> eventAppliers.register(null, 1, mockedApplier));
  }

  @Test
  void cannotRegisterApplierForNegativeVersion() {
    // given
    final var intent = mock(Intent.class);

    // then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> eventAppliers.register(intent, -1, mockedApplier));
  }

  @Test
  void cannotRegisterApplierForNonEvent() {
    // given
    final var nonEvent = mock(Intent.class);

    // when
    when(nonEvent.isEvent()).thenReturn(false);

    // then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> eventAppliers.register(nonEvent, 1, mockedApplier));
  }

  @Test
  void cannotOverrideApplierForSameIntentAndVersion() {
    // given
    final var intent = mock(Intent.class);
    when(intent.isEvent()).thenReturn(true);

    // when
    eventAppliers.register(intent, 1, mockedApplier);

    // then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> eventAppliers.register(intent, 1, anotherMockedApplier));
  }

  @Test
  void shouldOnlyRegisterAppliersForEvents() {
    // given
    final var intents =
        Intent.INTENT_CLASSES.stream()
            .flatMap(c -> Arrays.stream(c.getEnumConstants()))
            // CheckpointIntent is not handled by the engine
            .filter(intent -> !(intent instanceof CheckpointIntent));

    // when
    eventAppliers.registerEventAppliers(mock(MutableProcessingState.class));

    // then
    assertThat(intents)
        .allSatisfy(
            intent -> {
              if (!intent.isEvent()) {
                assertThat(eventAppliers.getLatestVersion(intent))
                    .describedAs(
                        "Intent %s.%s is not an event but has a registered event applier",
                        intent.getClass().getSimpleName(), intent.name())
                    .isEqualTo(-1);
              }
            });
  }

  /**
   * Verifies that event applier source files match their golden copies.
   *
   * @see <a
   *     href="https://github.com/camunda/camunda/blob/main/docs/zeebe/event-applier-golden-files.md">Event
   *     Applier Golden Files</a>
   */
  @Nested
  class NoChangesTest {

    // Paths relative to repo root, for use in describedAs messages and GoldenFileUpdater.
    // Surefire runs from the module directory (zeebe/engine/), so we prefix with the module path.
    private static final String MODULE_PATH = "zeebe/engine/";
    private static final String EVENT_APPLIERS_FOLDER =
        "src/main/java/io/camunda/zeebe/engine/state/appliers";
    private static final String GOLDEN_FILES_FOLDER = "src/test/resources/state/appliers/golden";
    private static final String DOCS_URL =
        "https://github.com/camunda/camunda/blob/main/docs/zeebe/event-applier-golden-files.md";

    private static Stream<RegisteredApplier> registeredAppliers() {
      final var eventAppliers = new EventAppliers();
      eventAppliers.registerEventAppliers(mock(MutableProcessingState.class));
      return eventAppliers.getRegisteredAppliers().entrySet().stream()
          .flatMap(
              entry -> {
                final var intent = entry.getKey();
                return entry.getValue().entrySet().stream()
                    .map(
                        applierEntry -> {
                          final var version = applierEntry.getKey();
                          final var applier = applierEntry.getValue();
                          return new RegisteredApplier(intent, version, applier);
                        });
              });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("registeredAppliers")
    void shouldNotChangeImplementation(final RegisteredApplier registeredApplier)
        throws IOException {
      final var intent = registeredApplier.intent;
      final var version = registeredApplier.version;
      final var applier = registeredApplier.applier;
      final var valueTypeName = intent.getClass().getSimpleName().replace("Intent", "");

      // find source file
      final var applierClassName = applier.getClass().getSimpleName();
      final var applierSourceFile = "%s/%s.java".formatted(EVENT_APPLIERS_FOLDER, applierClassName);
      final var applierSourcePath = Paths.get(applierSourceFile);

      // find golden file
      final var goldenFilename = "%s_%s_v%s.golden".formatted(valueTypeName, intent, version);
      final var goldenFilePath = Paths.get("%s/%s".formatted(GOLDEN_FILES_FOLDER, goldenFilename));

      // repo-root-relative paths for use in copy commands
      final var repoSourcePath = MODULE_PATH + applierSourceFile;
      final var repoGoldenPath = MODULE_PATH + GOLDEN_FILES_FOLDER + "/" + goldenFilename;

      if (!goldenFilePath.toFile().exists()) {
        LOG.error("Golden file for {} does not exist.", applierClassName);
        final var copyCommand =
            applierSourcePath.toFile().exists()
                ? "  cp %s %s".formatted(repoSourcePath, repoGoldenPath)
                : "  echo -n > %s".formatted(repoGoldenPath);
        assertThat(goldenFilePath.toFile())
            .describedAs(
                """
                Event appliers must not change after release.
                Expected golden file to exist for %s (v%d) but it was missing — create it with:
                %s
                For bulk updates, run GoldenFileUpdater.main() in NoChangesTest — \
                this overwrites ALL golden files, review each failure first.
                See %s"""
                    .formatted(applierClassName, version, copyCommand, DOCS_URL))
            .exists();
      }

      // if the source file does not exist, expect that the golden file's content is empty
      // this ensures we also check golden files for NOOP appliers
      final String goldenFileContents = Files.readString(goldenFilePath);
      if (!applierSourcePath.toFile().exists()) {
        if (!goldenFileContents.isEmpty()) {
          LOG.error("Golden file is not empty, even though this is a NOOP applier.");
        }
        assertThat(goldenFileContents)
            .describedAs(
                """
                Event appliers must not change after release.
                Expected empty golden file for NOOP applier %s (v%d) but it has content.
                The most common fix is to register a new applier version, \
                not update the golden file.
                If you're sure the golden file should change:
                  echo -n > %s
                For bulk updates, run GoldenFileUpdater.main() in NoChangesTest — \
                this overwrites ALL golden files, review each failure first.
                See %s"""
                    .formatted(applierClassName, version, repoGoldenPath, DOCS_URL))
            .isEmpty();
        return;
      }

      // compare the source and golden files
      final String sourceContents = Files.readString(applierSourcePath);
      if (!sourceContents.equals(goldenFileContents)) {
        LOG.error("Golden file for {} does not match source file.", applierClassName);
      }
      assertThat(sourceContents)
          .describedAs(
              """
              Event appliers must not change after release.
              Expected golden file to match source for %s (v%d) but they differ.
              The most common fix is to register a new applier version, \
              not update the golden file.
              If you're sure the golden file should change:
                cp %s %s
              For bulk updates, run GoldenFileUpdater.main() in NoChangesTest — \
              this overwrites ALL golden files, review each failure first.
              See %s"""
                  .formatted(applierClassName, version, repoSourcePath, repoGoldenPath, DOCS_URL))
          .isEqualTo(goldenFileContents);
    }

    /**
     * Utility to update all golden files at once. Run from an IDE or command line.
     *
     * <p>Usage: Run {@code GoldenFileUpdater.main()} — it iterates all registered appliers and
     * copies each source file to its golden file (or creates an empty golden file for NOOPs).
     *
     * <p>To run this, make sure to add the following VM option to your run configuration:
     *
     * <pre>--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED</pre>
     */
    public static class GoldenFileUpdater {

      public static void main(final String[] args) throws IOException {
        System.out.println(
            """
            WARNING: This overwrites ALL golden files unconditionally.
            Only run this after reviewing each failing test case.
            Updating golden files for changed appliers may hide breaking changes.

            Do you want to continue? (y/N)""");

        try (final var scanner = new java.util.Scanner(System.in)) {
          final var input = scanner.nextLine().trim();
          if (!input.equalsIgnoreCase("y")) {
            System.out.println("Aborted.");
            return;
          }
        }

        // Use a JDK Proxy instead of Mockito so this main() method can run without a test
        // framework. All methods return null (or 0 for primitives) because applier constructors
        // only store these references and never call methods on them.
        @SuppressWarnings("SuspiciousInvocationHandlerImplementation")
        final var state =
            (MutableProcessingState)
                Proxy.newProxyInstance(
                    MutableProcessingState.class.getClassLoader(),
                    new Class<?>[] {MutableProcessingState.class},
                    (proxy, method, params) -> method.getReturnType().isPrimitive() ? 0 : null);

        final var eventAppliers = new EventAppliers();
        eventAppliers.registerEventAppliers(state);

        final var basePath = resolveBasePath();
        final var goldenDir = basePath.resolve(GOLDEN_FILES_FOLDER);
        Files.createDirectories(goldenDir);

        for (final var intentEntry : eventAppliers.getRegisteredAppliers().entrySet()) {
          final var intent = intentEntry.getKey();
          final var valueTypeName = intent.getClass().getSimpleName().replace("Intent", "");

          for (final var applierEntry : intentEntry.getValue().entrySet()) {
            final var version = applierEntry.getKey();
            final var applier = applierEntry.getValue();
            final var applierClassName = applier.getClass().getSimpleName();

            final var goldenFilename = "%s_%s_v%s.golden".formatted(valueTypeName, intent, version);
            final var goldenFilePath = goldenDir.resolve(goldenFilename);

            final var sourcePath =
                basePath.resolve("%s/%s.java".formatted(EVENT_APPLIERS_FOLDER, applierClassName));
            if (Files.exists(sourcePath)) {
              Files.copy(
                  sourcePath, goldenFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
              System.out.printf("Copied %s.java -> %s%n", applierClassName, goldenFilename);
            } else {
              Files.writeString(goldenFilePath, "");
              System.out.printf("Created empty %s (NOOP applier)%n", goldenFilename);
            }
          }
        }
      }

      /**
       * Determines the base path to the module directory by checking whether the CWD is the repo
       * root or the module directory. Fails fast if neither matches.
       */
      private static Path resolveBasePath() {
        // CWD is the module directory (e.g. IntelliJ default, or Surefire)
        if (Paths.get(EVENT_APPLIERS_FOLDER).toFile().isDirectory()) {
          return Paths.get("");
        }
        // CWD is the repo root
        if (Paths.get(MODULE_PATH + EVENT_APPLIERS_FOLDER).toFile().isDirectory()) {
          return Paths.get(MODULE_PATH);
        }
        throw new IllegalStateException(
            """
            Cannot find event appliers folder. \
            Run GoldenFileUpdater from the repo root or the %s module directory."""
                .formatted(MODULE_PATH));
      }
    }

    private record RegisteredApplier(Intent intent, int version, TypedEventApplier applier) {
      @Override
      public String toString() {
        final var valueType = intent.getClass().getSimpleName().replace("Intent", "");
        final var applierName =
            applier == EventAppliers.NOOP_EVENT_APPLIER
                ? "NOOP applier"
                : applier.getClass().getSimpleName();
        return "%s for '%s.%s' (v%d)".formatted(applierName, valueType, intent, version);
      }
    }
  }
}
