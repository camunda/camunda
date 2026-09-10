/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.Timestamps;
import io.camunda.zeebe.dynamic.config.PersistedClusterConfiguration;
import io.camunda.zeebe.dynamic.config.PersistedClusterConfiguration.Header;
import io.camunda.zeebe.dynamic.config.PersistedCurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.protocol.Topology;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

  public static Topology.CurrentClusterConfiguration parseCurrentClusterConfiguration(
      final String json) throws InvalidProtocolBufferException {
    final var builder = Topology.CurrentClusterConfiguration.newBuilder();
    JsonFormat.parser().merge(json, builder);
    return builder.build();
  }

  /**
   * Saves the input JSON under the header version the file already has, so that an edit hands the
   * broker back the same format it wrote and a print/edit/save round-trip stays lossless.
   */
  private void saveFile(final Path file) throws IOException {
    final var json = source == null ? readInputFromStdin() : readInputFromFile();
    final var version = versionOf(file);

    final Message protobuf =
        switch (version) {
          case PersistedClusterConfiguration.VERSION -> parseTopology(json);
          case PersistedCurrentClusterConfiguration.VERSION ->
              parseCurrentClusterConfiguration(json);
          default -> throw unsupportedVersion(file, version);
        };

    PersistedClusterConfiguration.writeToFile(protobuf.toByteArray(), file, version);
  }

  private void printFile(final Path path) throws IOException {
    final var content = Files.readAllBytes(path);
    final var header = Header.parseAnyVersion(content, path);
    spec.commandLine().getErr().println("Header: " + header);
    final var buffer =
        ByteBuffer.wrap(content, Header.HEADER_LENGTH, content.length - Header.HEADER_LENGTH);

    final Message protobuf =
        switch (header.version()) {
          case PersistedClusterConfiguration.VERSION -> Topology.ClusterTopology.parseFrom(buffer);
          case PersistedCurrentClusterConfiguration.VERSION ->
              Topology.CurrentClusterConfiguration.parseFrom(buffer);
          default -> throw unsupportedVersion(path, header.version());
        };

    final var json = convertToJson(protobuf);
    spec.commandLine().getOut().println(json);
  }

  private byte versionOf(final Path path) throws IOException {
    if (!Files.exists(path)) {
      return PersistedCurrentClusterConfiguration.VERSION;
    }
    return Header.parseAnyVersion(Files.readAllBytes(path), path).version();
  }

  private static IllegalArgumentException unsupportedVersion(final Path path, final byte version) {
    return new IllegalArgumentException(
        "Topology file %s has header version '%s', but only versions '%s' and '%s' are supported"
            .formatted(
                path,
                version,
                PersistedClusterConfiguration.VERSION,
                PersistedCurrentClusterConfiguration.VERSION));
  }

  public String convertToJson(final Message message) throws InvalidProtocolBufferException {
    final var printable = withPrintableTimestamps(message);
    if (!printable.equals(message)) {
      spec.commandLine()
          .getErr()
          .println("Out-of-range timestamp detected, printing it as the epoch instead");
    }

    return JsonFormat.printer()
        .includingDefaultValueFields()
        .preservingProtoFieldNames()
        .print(printable);
  }

  private static Message withPrintableTimestamps(final Message message) {
    if (message instanceof final Timestamp timestamp) {
      return Timestamps.isValid(timestamp) ? timestamp : Timestamp.getDefaultInstance();
    }

    final var builder = message.toBuilder();
    message
        .getAllFields()
        .forEach(
            (field, value) -> {
              if (field.getJavaType() == JavaType.MESSAGE) {
                builder.setField(field, withPrintableTimestamps(field, value));
              }
            });
    return builder.build();
  }

  /** A map field arrives here as its list of entry messages, so map values are covered as well. */
  private static Object withPrintableTimestamps(final FieldDescriptor field, final Object value) {
    if (!field.isRepeated()) {
      return withPrintableTimestamps((Message) value);
    }
    return ((List<?>) value)
        .stream().map(entry -> withPrintableTimestamps((Message) entry)).toList();
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
