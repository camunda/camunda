/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Prints a system partition snapshot as readable JSON. The snapshot file stores every value as
 * base64 encoded MessagePack under its numeric key id; this resolves the ids to {@link
 * SystemPartitionKeyType} names and decodes the values.
 *
 * <p>Usage: {@code SystemPartitionSnapshotReader <snapshot directory | system-partition.json>}
 */
public final class SystemPartitionSnapshotReader {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private SystemPartitionSnapshotReader() {}

  public static void main(final String[] args) throws IOException {
    if (args.length != 1) {
      System.err.println(
          "Usage: SystemPartitionSnapshotReader <snapshot directory | "
              + SystemPartitionStateController.STATE_FILE
              + ">");
      System.exit(1);
    }

    System.out.println(MAPPER.writeValueAsString(read(Path.of(args[0]))));
  }

  /** Returns the snapshot's version and its entries by key name, with the values decoded. */
  public static ObjectNode read(final Path snapshot) throws IOException {
    final var file =
        Files.isDirectory(snapshot)
            ? snapshot.resolve(SystemPartitionStateController.STATE_FILE)
            : snapshot;
    final var state = MAPPER.readValue(file.toFile(), SystemPartitionState.class);

    final var entries = new TreeMap<String, Object>();
    for (final Map.Entry<Long, byte[]> entry : state.entries().entrySet()) {
      final var name =
          SystemPartitionKeyType.fromId(entry.getKey())
              .map(Enum::name)
              .orElse("UNKNOWN_" + entry.getKey());
      entries.put(name, decode(entry.getValue()));
    }

    final var result = MAPPER.createObjectNode().put("version", state.version());
    result.set("entries", MAPPER.valueToTree(entries));
    return result;
  }

  private static Object decode(final byte[] value) {
    try {
      return MAPPER.readTree(MsgPackConverter.convertToJson(value));
    } catch (final Exception e) {
      return "<undecodable: " + e.getMessage() + ">";
    }
  }
}
