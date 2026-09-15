/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.db.impl.rocksdb.transaction.RawTransactionalColumnFamily;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "summary", description = "Print entry counts for all known state column families")
public final class StateSummaryCommand implements Callable<Integer> {

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
      names = {"--runtime"},
      description = "Optional runtime directory used while reading the snapshot")
  private Path runtimePath;

  @Spec private CommandSpec spec;

  @Override
  public Integer call() throws Exception {
    final var columnFamilies = new ArrayList<Map<String, Object>>();
    SnapshotReader.read(
        root,
        snapshotId,
        runtimePath,
        db -> {
          final var context = db.createContext();
          for (final var columnFamily : ZbColumnFamilies.values()) {
            final var rawColumnFamily = new RawTransactionalColumnFamily(db, columnFamily);
            final long[] count = {0};
            rawColumnFamily.forEachKey(context, (key, offset, length) -> {
              count[0]++;
              return true;
            });

            final var entry = new LinkedHashMap<String, Object>();
            entry.put("name", columnFamily.name());
            entry.put("id", columnFamily.getValue());
            entry.put("scope", columnFamily.partitionScope().name());
            entry.put("entries", count[0]);
            columnFamilies.add(entry);
          }
          return null;
        });

    final var output = new LinkedHashMap<String, Object>();
    output.put("snapshot", snapshotId);
    output.put("columnFamilies", columnFamilies);
    final var out = spec.commandLine().getOut();
    new ObjectMapper().writeValue(out, output);
    out.println();
    out.flush();
    return 0;
  }
}
