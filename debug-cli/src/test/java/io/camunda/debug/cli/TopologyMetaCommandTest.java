/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

public class TopologyMetaCommandTest {

  @TempDir Path tempDir;
  CommandLine commandLine;
  StringWriter err;
  StringWriter out;

  @BeforeEach
  public void setup() {
    err = new StringWriter();
    out = new StringWriter();
    commandLine =
        new CommandLine(new Main()).setErr(new PrintWriter(err)).setOut(new PrintWriter(out));
  }

  @ParameterizedTest
  @ValueSource(strings = {"f", "file", "r", "root"})
  public void shouldReadTopologyFileCorrectly(final String method) {
    // given
    runWithMethod(method);
    final var json = out.toString();
    assertThat(json).isNotEmpty();
    assertThatNoException()
        .isThrownBy(
            () -> {
              TopologyMetaCommand.parseTopology(json);
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"f", "file", "r", "root"})
  public void shouldUpdateTopologyFile(final String method) throws IOException {
    // given
    runWithMethod(method);
    final var json = out.toString();
    // convert the json to protobuf
    final var protobuf = TopologyMetaCommand.parseTopology(json);
    final var builder = protobuf.toBuilder();
    builder.setVersion(999L);
    final var modifiedJson = new TopologyMetaCommand().convertToJson(builder.build());

    final var file = tempDir.resolve("scratch.json").toFile();
    file.createNewFile();
    Files.write(file.toPath(), modifiedJson.getBytes());
    out.close();
    out = new StringWriter();

    // when
    runWithMethod(method, "-s", "--source", file.toPath().toString());

    // then
    out.close();
    out = new StringWriter();
    commandLine.setOut(new PrintWriter(out));
    runWithMethod(method);
    final var changedJson = out.toString();
    // there's a newline difference, so trim() is needed
    assertThat(changedJson.trim()).isEqualTo(modifiedJson.trim());
  }

  public void runWithMethod(final String method, final String... extraArgs) {
    // Get the file path from test resources
    final var resourceUrl = getClass().getClassLoader().getResource(".topology.meta");
    Assertions.assertThat(resourceUrl).isNotNull();
    final var filePath = resourceUrl.getPath();

    final var args = new ArrayList<String>();
    args.add("topology");
    switch (method) {
      case "file":
        args.addAll(List.of("-v", "--file", filePath));
        break;
      case "f":
        args.addAll(List.of("-v", "-f", filePath));
        break;
      case "r":
        args.addAll(List.of("-v", "-r", Path.of(filePath).getParent().toString()));
        break;
      case "root":
        args.addAll(List.of("-v", "--root", Path.of(filePath).getParent().toString()));
        break;
      default:
        throw new IllegalArgumentException("Unknown method: " + method);
    }
    args.addAll(Arrays.stream(extraArgs).toList());
    commandLine.execute(args.toArray(new String[0]));
  }
}
