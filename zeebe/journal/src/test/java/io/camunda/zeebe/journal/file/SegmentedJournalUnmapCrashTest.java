/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that {@link SegmentedJournal#close()} can segfault when a reader touches a segment's
 * mapped buffer while close() is unmapping it.
 *
 * <p>Disabled by default: it forks a JVM and spins for up to a couple of minutes.
 */
@Disabled("Slow - spins to reproduce segfault")
class SegmentedJournalUnmapCrashTest {

  @TempDir Path workDir;

  @Test
  void closeUnmapDuringConcurrentReadIsJvmFatal() throws Exception {
    // given
    final var javaBin =
        Path.of(System.getProperty("java.home"), "bin", "java").toAbsolutePath().toString();
    final var classpath = System.getProperty("java.class.path");

    final var command = new ArrayList<String>(List.of(javaBin, "-cp", classpath));
    final var parentArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
    for (int i = 0; i < parentArgs.size(); i++) {
      final var arg = parentArgs.get(i);
      if (arg.startsWith("--add-opens") || arg.startsWith("--add-exports")) {
        command.add(arg);
        // handle the space-separated form: "--add-opens" followed by "module/pkg=target"
        if (arg.indexOf('=') < 0 && i + 1 < parentArgs.size()) {
          command.add(parentArgs.get(++i));
        }
      }
    }
    command.add("-XX:ErrorFile=" + workDir.resolve("hs_err_%p.log"));
    command.add(SegmentedJournalUnmapCrashHarness.class.getName());
    final var processBuilder = new ProcessBuilder(command).redirectErrorStream(true);

    // when
    final var process = processBuilder.start();
    final var output = new String(process.getInputStream().readAllBytes());
    final var exited = process.waitFor(3, TimeUnit.MINUTES);
    if (!exited) {
      process.destroyForcibly();
    }

    // then
    assertThat(output)
        .describedAs("the JVM must crash")
        .containsAnyOf(
            "SIGSEGV", "SIGBUS", "A fatal error has been detected", "EXCEPTION_ACCESS_VIOLATION");
  }
}
