/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Timestamp;
import io.camunda.zeebe.dynamic.config.PersistedCurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.impl.protocol.Topology;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
  public void setup() throws IOException {
    err = new StringWriter();
    out = new StringWriter();
    commandLine =
        new CommandLine(new Main()).setErr(new PrintWriter(err)).setOut(new PrintWriter(out));
  }

  @ParameterizedTest
  @ValueSource(strings = {"f", "file", "r", "root"})
  public void shouldReadConfigurationFileWithHeaderVersion2(final String method)
      throws IOException {
    // given
    final var configuration = configurationWith(timestamp(1758000000L));
    final var file =
        writeConfigurationFile(configuration, PersistedCurrentClusterConfiguration.VERSION);

    // when
    final var exitCode = runWithMethod(file, method);

    // then
    assertThat(exitCode).isZero();
    assertThat(TopologyMetaCommand.parseCurrentClusterConfiguration(out.toString()))
        .isEqualTo(configuration);
  }

  @Test
  public void shouldUpdateConfigurationFileWithHeaderVersion2() throws IOException {
    // given
    final var file =
        writeConfigurationFile(
            configurationWith(timestamp(1758000000L)),
            PersistedCurrentClusterConfiguration.VERSION);
    final var updated =
        configurationWith(timestamp(1758000000L)).toBuilder().setVersion(999L).build();
    final var source =
        Files.writeString(
            tempDir.resolve("scratch.json"), new TopologyMetaCommand().convertToJson(updated));

    // when
    final var exitCode =
        commandLine.execute("topology", "-s", "-f", file.toString(), "--source", source.toString());

    // then
    assertThat(exitCode).isZero();
    assertThat(Files.readAllBytes(file)[0]).isEqualTo(PersistedCurrentClusterConfiguration.VERSION);

    out = new StringWriter();
    commandLine.setOut(new PrintWriter(out));
    commandLine.execute("topology", "-f", file.toString());
    assertThat(TopologyMetaCommand.parseCurrentClusterConfiguration(out.toString()))
        .isEqualTo(updated);
  }

  @Test
  public void shouldWriteHeaderVersion2ForNewFile() throws IOException {
    // given
    final var configuration = configurationWith(timestamp(1758000000L));
    final var source =
        Files.writeString(
            tempDir.resolve("new.json"), new TopologyMetaCommand().convertToJson(configuration));
    final var file = tempDir.resolve("new.topology.meta");

    // when
    final var exitCode =
        commandLine.execute("topology", "-s", "-f", file.toString(), "--source", source.toString());

    // then
    assertThat(exitCode).isZero();
    assertThat(Files.readAllBytes(file)[0]).isEqualTo(PersistedCurrentClusterConfiguration.VERSION);
  }

  @Test
  public void shouldPrintConfigurationWithOutOfRangeTimestamp() throws IOException {
    // given
    final var file =
        writeConfigurationFile(
            configurationWith(timestamp(Long.MAX_VALUE)),
            PersistedCurrentClusterConfiguration.VERSION);

    // when
    final var exitCode = commandLine.execute("topology", "-f", file.toString());

    // then
    assertThat(exitCode).isZero();
    assertThat(
            TopologyMetaCommand.parseCurrentClusterConfiguration(out.toString())
                .getGlobalConfiguration()
                .getMembersMap()
                .get("0")
                .getLastUpdated())
        .isEqualTo(Timestamp.getDefaultInstance());
  }

  @Test
  public void shouldRejectLegacyHeaderVersion1() throws IOException {
    // given
    final var file = writeConfigurationFile(configurationWith(timestamp(1758000000L)), (byte) 1);

    // when
    final var exitCode = commandLine.execute("topology", "-f", file.toString());

    // then
    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  public void shouldRejectUnknownHeaderVersion() throws IOException {
    // given
    final var file = writeConfigurationFile(configurationWith(timestamp(1758000000L)), (byte) 3);

    // when
    final var exitCode = commandLine.execute("topology", "-f", file.toString());

    // then
    assertThat(exitCode).isEqualTo(2);
  }

  private Timestamp timestamp(final long seconds) {
    return Timestamp.newBuilder().setSeconds(seconds).build();
  }

  private Topology.CurrentClusterConfiguration configurationWith(final Timestamp lastUpdated) {
    return Topology.CurrentClusterConfiguration.newBuilder()
        .setGlobalConfiguration(
            Topology.GlobalConfiguration.newBuilder()
                .setVersion(2L)
                .setClusterId("3ebb310d-d796-4200-a3f6-1c2ce2a1b64a")
                .putMembers(
                    "0",
                    Topology.BrokerState.newBuilder()
                        .setVersion(1L)
                        .setState(Topology.State.ACTIVE)
                        .setLastUpdated(lastUpdated)
                        .build()))
        .build();
  }

  private Path writeConfigurationFile(
      final Topology.CurrentClusterConfiguration configuration, final byte headerVersion)
      throws IOException {
    final var file = Files.createDirectories(tempDir.resolve("current")).resolve(".topology.meta");
    PersistedCurrentClusterConfiguration.writeToFile(configuration.toByteArray(), file);
    final var content = Files.readAllBytes(file);
    content[0] = headerVersion;
    Files.write(file, content);
    return file;
  }

  public int runWithMethod(final Path file, final String method, final String... extraArgs) {
    final var filePath = file.toString();

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
    return commandLine.execute(args.toArray(new String[0]));
  }
}
