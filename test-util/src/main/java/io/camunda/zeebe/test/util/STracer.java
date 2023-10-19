/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public final class STracer implements AutoCloseable {
  private final Process process;
  private final Path outputFile;

  private STracer(final Process process, final Path outputFile) {
    this.process = process;
    this.outputFile = outputFile;
  }

  public static STracer traceFor(final Syscall syscall, final Path outputFile) throws IOException {
    return traceFor(syscall, outputFile, false);
  }

  public static STracer traceFor(final Syscall syscall, final Path outputFile, final boolean debug)
      throws IOException {
    final var pid = ProcessHandle.current().pid();
    final var output = outputFile.toAbsolutePath().toString();
    final var builder =
        new ProcessBuilder()
            .command(
                "strace",
                "-y",
                "-f",
                "-e",
                "trace=" + syscall.id,
                "-o",
                output,
                "-p",
                String.valueOf(pid));

    if (debug) {
      builder.inheritIO();
    }

    return new STracer(builder.start(), outputFile);
  }

  @Override
  public void close() throws Exception {
    process.destroy();
    process.waitFor();
  }

  public List<FSyncTrace> fSyncTraces() throws IOException {
    try (final var reader = Files.newBufferedReader(outputFile)) {
      return reader.lines().filter(s -> s.contains("fsync")).map(FSyncTrace::of).toList();
    }
  }

  public record FSyncTrace(int pid, int fd, Path path, int result) {
    private static final Pattern FSYNC_CALL =
        Pattern.compile(
            "^(?<pid>[0-9]+)\\s+fsync\\((?<fd>[0-9]+)\\<(?<path>.+?)\\>\\)\\s+=\\s+(?<result>[0-9]+)$");

    public static FSyncTrace of(final String straceLine) {
      final var matcher = FSYNC_CALL.matcher(straceLine);
      if (!matcher.find()) {
        throw new IllegalArgumentException(
            "Expected line to match format of 'PID fsync(FD<PATH>) = RESULT', but '%s' does not match"
                .formatted(straceLine));
      }

      final var pid = Integer.parseInt(matcher.group("pid"));
      final var fd = Integer.parseInt(matcher.group("fd"));
      final var path = Path.of(matcher.group("path"));
      final var result = Integer.parseInt(matcher.group("result"));

      return new FSyncTrace(pid, fd, path, result);
    }
  }

  public enum Syscall {
    FSYNC("fsync");

    private final String id;

    Syscall(final String id) {
      this.id = id;
    }
  }
}
