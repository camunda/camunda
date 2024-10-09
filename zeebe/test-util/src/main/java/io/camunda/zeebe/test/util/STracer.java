/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.agrona.LangUtil;

/**
 * A utility class to manage <a
 * href="https://man7.org/linux/man-pages/man1/strace.1.html">strace</a> runs.
 *
 * <p>It allows tracing system calls during its run, which can be used to test that `fsync` or
 * `mysnc` are actually called.
 *
 * <p>NOTE: all methods to read back traces block up to 1 second. This is because communication
 * between this process (i.e. Java) and strace happens over a file, meaning it's asynchronous and
 * there may be some delay. As such, calling {@link #fSyncTraces()}, for example, will block up to 1
 * second
 *
 * <p>This is only usable iff:
 *
 * <ul>
 *   <li>strace is installed on your machine
 *   <li>Running {@code sysctl kernel.yama.ptace_scope} returns 0
 *   <li>If running on Docker, CAP_SYS_PTRACE is added to the capabilities
 *   <li>If running in Kubernetes, SYS_PTRACE is added to the container's security context
 *       capabilities
 * </ul>
 */
public final class STracer implements AutoCloseable {
  private final Process process;
  private final Path outputFile;

  private boolean isClosed;

  private STracer(final Process process, final Path outputFile) {
    this.process = process;
    this.outputFile = outputFile;
  }

  /**
   * Returns a tracer for the given system call which will output its result in the given output
   * file.
   *
   * <p>Make sure to close the tracer instance when you're finished.
   *
   * @param syscall the call to trace
   * @param outputFile the file to write to
   * @return a closeable tracer
   * @throws IOException if the output file cannot be created/accessed, or strace does not launch
   */
  public static STracer tracerFor(final Syscall syscall, final Path outputFile) throws IOException {
    final var pid = ProcessHandle.current().pid();
    final var output = outputFile.toAbsolutePath().toString();
    final var process =
        new ProcessBuilder()
            .command(
                "strace", "-fye", "trace=" + syscall.id, "-o", output, "-p", String.valueOf(pid))
            .redirectErrorStream(true)
            .start();

    if (!Files.exists(outputFile)) {
      try {
        Files.createFile(outputFile);
      } catch (final FileAlreadyExistsException ignored) {
        // ignored
      }
    }

    // wait until strace is attached, otherwise we risk a race condition where our system call was
    // called before and we'll never observe it
    try {
      Thread.startVirtualThread(() -> waitUntilAttached(process.getInputStream(), pid))
          .join(Duration.ofSeconds(5));
    } catch (final InterruptedException e) {
      LangUtil.rethrowUnchecked(e);
    }

    return new STracer(process, outputFile);
  }

  private static void waitUntilAttached(final InputStream input, final long pid) {
    String line;
    try (final var reader = new BufferedReader(new InputStreamReader(input))) {
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("strace: Process %d attached with".formatted(pid))) {
          break;
        }
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void close() throws Exception {
    if (isClosed) {
      return;
    }

    isClosed = true;
    process.destroy();
    process.waitFor();
  }

  /**
   * Returns the list of observed fsync traces.
   *
   * <p>NOTE: this method blocks up to 1 second for an incoming trace.
   *
   * @return the list of fsync calls observed
   */
  public Stream<FSyncTrace> fSyncTraces() {
    try {
      return Files.readAllLines(outputFile).stream()
          .filter(s -> s.contains("fsync") && !s.contains("resumed"))
          .map(FSyncTrace::of);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public record FSyncTrace(int pid, int fd, Path path) {
    private static final Pattern FSYNC_CALL =
        Pattern.compile("^(?<pid>[0-9]+)\\s+fsync\\((?<fd>[0-9]+)<(?<path>.+?)>.+$");

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

      return new FSyncTrace(pid, fd, path);
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
