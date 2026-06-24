/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.camunda.zeebe.dynamic.config.PersistedClusterConfiguration;
import io.camunda.zeebe.dynamic.config.PersistedClusterConfiguration.Header;
import io.camunda.zeebe.dynamic.config.protocol.Topology;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "topology",
    description = "Print or edit the topology.meta file from dynamic-config module")
public class TopologyMetaCommand extends CommonOptions implements Callable<Integer> {

  @Spec CommandSpec spec;

  @Option(
      names = {"-s", "--save"},
      description = "Save the json from std input into the file")
  private boolean save = false;

  @Option(
      names = {"-f", "--file"},
      description = "Path of the .topology.meta")
  private Path file;

  @Option(
      names = {"--source"},
      description = "The input file when saving it")
  private Path source;

  @Override
  public Integer call() throws Exception {
    try {
      final var file = validateArguments();
      if (verbose) {
        spec.commandLine().getErr().println("Path used: " + file);
      }

      if (save) {
        saveFile(file);

      } else {
        printFile(file);
      }
      return 0;
    } catch (final Exception e) {
      e.printStackTrace();
      return 2;
    }
  }

  private String readInput(final Scanner scanner) {
    final var jsonBuilder = new StringBuilder();
    while (scanner.hasNextLine()) {
      jsonBuilder.append(scanner.nextLine()).append("\n");
    }
    return jsonBuilder.toString();
  }

  private String readInputFromStdin() {
    try (final var scanner = new Scanner(System.in)) {
      return readInput(scanner);
    }
  }

  private String readInputFromFile() throws IOException {
    try (final var fis = new FileInputStream(source.toFile());
        final var scanner = new Scanner(fis)) {
      return readInput(scanner);
    }
  }

  public static Topology.ClusterTopology parseTopology(final String json)
      throws InvalidProtocolBufferException {
    final var builder = Topology.ClusterTopology.newBuilder();
    JsonFormat.parser().merge(json, builder);
    return builder.build();
  }

  private void saveFile(final Path file) throws IOException {
    final var json = source == null ? readInputFromStdin() : readInputFromFile();

    final var protobuf = parseTopology(json);

    final var bytes = protobuf.toByteArray();
    PersistedClusterConfiguration.writeToFile(bytes, file);
  }

  private void printFile(final Path path) throws IOException {
    final var content = Files.readAllBytes(path);
    final var header = PersistedClusterConfiguration.Header.parseFrom(content, path);
    spec.commandLine().getErr().println("Header: " + header);
    final var buffer =
        ByteBuffer.wrap(content, Header.HEADER_LENGTH, content.length - Header.HEADER_LENGTH);

    final var protobuf = Topology.ClusterTopology.parseFrom(buffer);
    final var json = convertToJson(protobuf);
    spec.commandLine().getOut().println(json);
  }

  public String convertToJson(final Topology.ClusterTopology topology) throws IOException {
    try {
      return JsonFormat.printer()
          .includingDefaultValueFields()
          .preservingProtoFieldNames()
          .print(topology);
    } catch (final IllegalArgumentException e) {
      spec.commandLine()
          .getErr()
          .println(
              "Invalid timestamp detected, fixing by setting lastUpdated to 0 for all members");

      final var builder = Topology.ClusterTopology.newBuilder(topology);

      // Create a new members map with lastUpdated set to 0
      builder.clearMembers();
      topology
          .getMembersMap()
          .forEach(
              (memberId, memberState) -> {
                final var fixedMemberState =
                    Topology.MemberState.newBuilder(memberState)
                        .setLastUpdated(
                            com.google.protobuf.Timestamp.newBuilder()
                                .setSeconds(0)
                                .setNanos(0)
                                .build())
                        .build();
                builder.putMembers(memberId, fixedMemberState);
              });

      final var fixedTopology = builder.build();

      try {
        return JsonFormat.printer()
            .includingDefaultValueFields()
            .preservingProtoFieldNames()
            .print(fixedTopology);
      } catch (final InvalidProtocolBufferException e2) {
        throw new IOException("Failed to parse JSON into ClusterTopology: " + e2.getMessage(), e2);
      }
    }
  }

  private Path validateArguments() {
    if (file != null) {
      return file;
    } else if (root != null) {
      return root.resolve(".topology.meta");
    }

    throw new IllegalArgumentException("Missing path, provide a path with either --root or --file");
  }
}
