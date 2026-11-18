/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.es9;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.test.util.logging.LogCapturer;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class Es9MigratorAppTest {

  @TempDir Path tempDir;

  static Stream<Arguments> modes() {
    return Stream.of(
        Arguments.of((Object) new String[] {"prepare", "--termination-log=/dev/null"}),
        Arguments.of((Object) new String[] {"migrate", "--group=oc"}),
        Arguments.of((Object) new String[] {"migrate", "--group=optimize"}),
        Arguments.of((Object) new String[] {"recover"}));
  }

  static Stream<Arguments> failures() {
    return Stream.of(
        Arguments.of(new ModeResult.NeedsHumanClean("some reason"), 10),
        Arguments.of(new ModeResult.NeedsHumanUnclean("some reason"), 20));
  }

  @ParameterizedTest
  @MethodSource("modes")
  void shouldExitZeroForEveryStubMode(final String[] args) {
    // when
    final var exitCode = Es9MigratorApp.execute(args);

    // then
    assertThat(exitCode).isZero();
  }

  @Test
  void shouldWriteEmptyGroupsTerminationMessageOnPrepare() {
    // given
    final var terminationLog = tempDir.resolve("termination-log");

    // when
    Es9MigratorApp.execute("prepare", "--termination-log=" + terminationLog);

    // then
    assertThat(terminationLog).hasContent("{\"groups\":[]}");
  }

  @Test
  void shouldMapEachResultToItsExitCode() {
    assertThat(ExitCode.of(new ModeResult.Done()).code()).isEqualTo(0);
    assertThat(ExitCode.of(new ModeResult.NeedsHumanClean("r")).code()).isEqualTo(10);
    assertThat(ExitCode.of(new ModeResult.NeedsHumanUnclean("r")).code()).isEqualTo(20);
  }

  @Test
  void shouldUseExitCodeOutsideTheContractForUnexpectedErrors() {
    // given
    final Mode failing =
        () -> {
          throw new IllegalStateException("boom");
        };

    // when
    final var exitCode = Es9MigratorApp.execute(failing);

    // then
    assertThat(exitCode)
        .isNotIn(
            ExitCode.DONE.code(),
            ExitCode.NEEDS_HUMAN_CLEAN.code(),
            ExitCode.NEEDS_HUMAN_UNCLEAN.code());
  }

  @ParameterizedTest
  @MethodSource("helpArgs")
  void shouldExitZeroOnHelp(final String[] args) {
    assertThat(Es9MigratorApp.execute(args)).isZero();
  }

  static Stream<Arguments> helpArgs() {
    return Stream.of(
        Arguments.of((Object) new String[] {"--help"}),
        Arguments.of((Object) new String[] {"prepare", "--help"}),
        Arguments.of((Object) new String[] {"migrate", "--help"}),
        Arguments.of((Object) new String[] {"recover", "--help"}));
  }

  @Test
  void shouldRejectMigrateWithoutValidGroup() {
    assertThat(Es9MigratorApp.execute("migrate")).isEqualTo(Es9MigratorApp.USAGE_ERROR);
    assertThat(Es9MigratorApp.execute("migrate", "--group=other"))
        .isEqualTo(Es9MigratorApp.USAGE_ERROR);
    assertThat(Es9MigratorApp.execute()).isEqualTo(Es9MigratorApp.USAGE_ERROR);
  }

  @ParameterizedTest
  @MethodSource("failures")
  void shouldLogReasonOfNonZeroExit(final ModeResult result, final int expectedCode) {
    try (final var logs = LogCapturer.capturing(Es9MigratorApp.class, Level.ERROR)) {
      // when
      final var exitCode = Es9MigratorApp.execute(() -> result);

      // then
      assertThat(exitCode).isEqualTo(expectedCode);
      assertThat(logs.messagesAt(Level.ERROR))
          .singleElement()
          .satisfies(m -> assertThat(m).contains("some reason"));
    }
  }

  @Test
  void shouldLogUnexpectedErrors() {
    // given
    final Mode failing =
        () -> {
          throw new IllegalStateException("boom");
        };

    try (final var logs = LogCapturer.capturing(Es9MigratorApp.class, Level.ERROR)) {
      // when
      Es9MigratorApp.execute(failing);

      // then
      assertThat(logs.messagesAt(Level.ERROR))
          .singleElement()
          .satisfies(m -> assertThat(m).contains("Unexpected error"));
    }
  }
}
