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

  @Nested
  class NoChangesTest {

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

    /**
     * EventAppliers are not allowed to change (with a few exceptions). This test ensures that event
     * appliers don't change by verifying source files of the event applier classes match golden
     * copies.
     */
    @ParameterizedTest
    @MethodSource("registeredAppliers")
    void shouldNotChangeImplementation(final RegisteredApplier registeredApplier)
        throws IOException {
      final var eventAppliersFolder = "src/main/java/io/camunda/zeebe/engine/state/appliers";
      final var goldenFilesFolder = "src/test/resources/state/appliers/golden";

      final var intent = registeredApplier.intent;
      final var version = registeredApplier.version;
      final var applier = registeredApplier.applier;
      final var valueTypeName = intent.getClass().getSimpleName().replace("Intent", "");

      // find source file
      final var applierClassName = applier.getClass().getSimpleName();
      final var applierSourceFile = "%s/%s.java".formatted(eventAppliersFolder, applierClassName);
      final var applierSourcePath = Paths.get(applierSourceFile);

      // find golden file
      final var goldenFilename = "%s_%s_v%s.golden".formatted(valueTypeName, intent, version);
      final var goldenFilePath = Paths.get("%s/%s".formatted(goldenFilesFolder, goldenFilename));
      if (!goldenFilePath.toFile().exists()) {
        LOG.error("Golden file for {} does not exist.", applierClassName);
        printWarningAboutChangingGoldenFiles();
        printCommandToCreateGoldenFile(applierSourcePath, goldenFilePath);
      }
      assertThat(goldenFilePath.toFile())
          .describedAs("Expected to find golden file for %s", applierClassName)
          .exists();

      // if the source file does not exist, expect that the golden file's content is empty
      // this ensures we also check golden files for NOOP appliers
      final String goldenFileContents = Files.readString(goldenFilePath);
      if (!applierSourcePath.toFile().exists()) {
        if (!goldenFileContents.isEmpty()) {
          LOG.error("Golden file is not empty, even though this is a NOOP applier.");
          printWarningAboutChangingGoldenFiles();
          printCommandToCreateGoldenFile(applierSourcePath, goldenFilePath);
        }
        assertThat(goldenFileContents).describedAs("Expected an empty golden file").isEmpty();
        return;
      }

      // compare the source and golden files
      final String sourceContents = Files.readString(applierSourcePath);
      if (!sourceContents.equals(goldenFileContents)) {
        LOG.error("Golden file for {} does not match source file.", applierClassName);
        printWarningAboutChangingGoldenFiles();
        printCommandToCreateGoldenFile(applierSourcePath, goldenFilePath);
      }
      assertThat(sourceContents)
          .describedAs("Expected source file of class %s to match golden copy", applierClassName)
          .isEqualTo(goldenFileContents);
    }

    private static void printWarningAboutChangingGoldenFiles() {
      LOG.warn(
          """
          Event appliers are not allowed to change once registered and released/used in production.
          The golden files exist to ensure that we carefully review changes to the appliers.

          Context: In Zeebe, an event must be replayed in the exact same way as it was applied to
          the state when it was initially written. This ensures that we can rebuild the state from
          all events by replaying them. Changes to the appliers can lead to replaying the events
          differently when updating to a new version. This can lead to serious bugs as the leader
          and followers may be ending up with different state. It is thus vital that we find ways
          to avoid such bugs. This failing test is one such way.

          But the test failed! What to do now? Please consider the following scenarios:

          1. If you've introduced a new event applier in another version, ported it here, and then
          found that the golden file is different, then you need to carefully consider the case:

          - If the applier was backported: this is a breaking change, and we need to
            abandon it. Please rollback the change in the newer version. Instead, register a new
            applier version, and ensure that is is available in all newer versions before adding
            it here.

          - If the applier was forward ported: you may have found a critical bug if the current
            code is already released. In that case, we need to carefully consider the changes and
            see if we can allow them or not. There's no standard process here. If the current code
            is not released yet, we can simply update the golden file to align with the older
            version.

          2. If you've introduced a new event applier, but an empty golden file already existed:

          - You're trying to change the applier. Please register a new version instead of adjusting
            the golden file. Please ensure that you also register the new applier in all newer minor
            versions.

          3. If you've introduced a new event applier, and the golden file didn't exist yet:

          - You can simply create a new golden file. You can safely register a new version. Please
            ensure that you also register the new applier in all newer minor versions.

          Lastly, there are a few cases where changing the event applier is allowed:

          - If the changes are purely cosmetic, such as changing comments or refactoring.

          - If the change doesn't strictly need to be consistent between versions, such as storing a
            new optional field that is always used in a same way and where it's acceptable that the
            field may or may not be present in the state. However, we should always be cautious of
            these cases, and consider whether they are truly safe.
          """);
    }

    private static void printCommandToCreateGoldenFile(
        final Path applierSourcePath, final Path goldenFilePathSystem) {
      // use absolute paths so it's easy to run anywhere
      final var sourcePath = applierSourcePath.toAbsolutePath();
      final var targetPath = goldenFilePathSystem.toAbsolutePath();
      if (Files.exists(sourcePath)) {
        LOG.info(
            """
            To create/overwrite the golden file, run the following command:
            cp {} {}""",
            sourcePath,
            targetPath);
      } else {
        // if there is no source file, we expect the golden file to be empty
        LOG.info(
            """
            To create/overwrite the golden file, run the following command:
            echo -n > {}""",
            targetPath);
      }
    }

    private record RegisteredApplier(Intent intent, int version, TypedEventApplier applier) {}
  }
}
