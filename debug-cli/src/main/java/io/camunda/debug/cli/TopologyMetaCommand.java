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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "topology",
    description = "Print or edit the topology.meta file from dynamic-config module")
public class TopologyMetaCommand implements Callable<Integer> {

  @Option(
      names = {"-s", "--save"},
      description = "Save the json from std input into the file")
  private boolean save = false;

  @Option(
      names = {"-f", "--file"},
      description = "Path of the .topology.meta")
  private Path file;

  @Override
  public Integer call() throws Exception {
    try {
      validateArguments();
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

  private void saveFile(final Path file) throws IOException {
    final var scanner = new Scanner(System.in);
    final var jsonBuilder = new StringBuilder();

    while (scanner.hasNextLine()) {
      jsonBuilder.append(scanner.nextLine()).append("\n");
    }
    scanner.close();
    final var builder = Topology.ClusterTopology.newBuilder();
    JsonFormat.parser().merge(jsonBuilder.toString(), builder);
    final var protobuf = builder.build();

    final var bytes = protobuf.toByteArray();
    PersistedClusterConfiguration.writeToFile(bytes, file);
  }

  private void printFile(final Path path) throws IOException {
    final var content = Files.readAllBytes(path);
    final var header = PersistedClusterConfiguration.Header.parseFrom(content, path);
    System.err.println("Header: " + header);
    final var buffer =
        ByteBuffer.wrap(content, Header.HEADER_LENGTH, content.length - Header.HEADER_LENGTH);

    final var protobuf = Topology.ClusterTopology.parseFrom(buffer);
    final var json = convertToJson(protobuf);
    System.out.println(json);
  }

  private String convertToJson(final Topology.ClusterTopology topology) throws IOException {
    try {
      return JsonFormat.printer()
          .includingDefaultValueFields()
          .preservingProtoFieldNames()
          .print(topology);
    } catch (final IllegalArgumentException e) {
      System.err.println(
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

  private void validateArguments() {
    if (file == null) {
      throw new IllegalArgumentException("Missing path");
    }
  }
}
