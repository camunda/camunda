/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.db.impl.rocksdb.transaction.RawTransactionalColumnFamily;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "list", description = "List raw entries from a state column family")
public final class StateListCommand implements Callable<Integer> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final MessagePackFactory MESSAGE_PACK_FACTORY = new MessagePackFactory();
  private static final ObjectMapper MESSAGE_PACK_MAPPER = new ObjectMapper(MESSAGE_PACK_FACTORY);

  @Option(
      names = {"-r", "--root"},
      description = "Path of the partition directory containing 'snapshots/'",
      required = true)
  private Path root;

  @Option(
      names = {"-s", "--snapshot"},
      description = "Id of the snapshot directory to read",
      required = true)
  private String snapshotId;

  @Option(
      names = {"--column-family"},
      description = "Column-family name, for example INCIDENTS",
      required = true)
  private String columnFamilyName;

  @Option(
      names = {"--runtime"},
      description = "Optional runtime directory used while reading the snapshot")
  private Path runtimePath;

  @Option(
      names = {"--limit"},
      description = "Maximum number of entries to print (default: ${DEFAULT-VALUE})",
      defaultValue = "1000")
  private int limit;

  @Option(
      names = {"--key-format"},
      description = "Key format: default, hex, or a format string using s/l/i/b/B",
      defaultValue = "default")
  private String keyFormat;

  @Spec private CommandSpec spec;

  @Override
  public Integer call() throws Exception {
    if (limit <= 0) {
      spec.commandLine().getErr().println("--limit must be greater than 0");
      return 1;
    }

    final ZbColumnFamilies columnFamily;
    try {
      columnFamily = ZbColumnFamilies.valueOf(columnFamilyName.trim().toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException e) {
      spec.commandLine().getErr().println("Unknown column family: " + columnFamilyName);
      return 1;
    }

    final List<StateEntry> entries = new ArrayList<>();
    final var keyFormatter = StateKeyFormatters.forColumnFamily(columnFamily, keyFormat);
    final var truncated = new boolean[1];
    SnapshotReader.read(
        root,
        snapshotId,
        runtimePath,
        db -> {
          final var context = db.createContext();
          final var rawColumnFamily = new RawTransactionalColumnFamily(db, columnFamily);
          rawColumnFamily.forEach(
              context,
              (key, keyOffset, keyLength, value, valueOffset, valueLength) -> {
                if (entries.size() == limit) {
                  truncated[0] = true;
                  return false;
                }
                final var decodedValue = decodeValue(value, valueOffset, valueLength);
                entries.add(
                    new StateEntry(
                        keyFormatter.format(key),
                        hex(key, 8, keyLength - 8),
                        decodedValue.json(),
                        decodedValue.hex()));
                return true;
              });
          return null;
        });

    final var output = new LinkedHashMap<String, Object>();
    output.put("snapshot", snapshotId);
    output.put("columnFamily", columnFamily.name());
    output.put("entries", entries);
    output.put("truncated", truncated[0]);
    final PrintWriter out = spec.commandLine().getOut();
    OBJECT_MAPPER.writeValue(out, output);
    out.println();
    out.flush();
    return 0;
  }

  private static String hex(final byte[] bytes, final int offset, final int length) {
    final var result = new StringBuilder(length * 2);
    for (int index = offset; index < offset + length; index++) {
      result.append("%02x".formatted(bytes[index]));
    }
    return result.toString();
  }

  private static Value decodeValue(final byte[] value, final int offset, final int length) {
    final var valueBytes = Arrays.copyOfRange(value, offset, offset + length);
    try (final JsonParser parser = MESSAGE_PACK_FACTORY.createParser(valueBytes)) {
      parser.setCodec(MESSAGE_PACK_MAPPER);
      if (parser.nextToken() == null) {
        return new Value(null, hex(valueBytes, 0, valueBytes.length));
      }
      final JsonNode json = parser.readValueAsTree();
      if (json == null) {
        return new Value(null, hex(valueBytes, 0, valueBytes.length));
      }
      return new Value(json, null);
    } catch (final RuntimeException | java.io.IOException e) {
      return new Value(null, hex(valueBytes, 0, valueBytes.length));
    }
  }

  record StateEntry(String key, String keyHex, JsonNode value, String valueHex) {}

  record Value(JsonNode json, String hex) {}
}
