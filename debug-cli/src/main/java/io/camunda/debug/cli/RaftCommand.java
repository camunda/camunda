/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli;

import io.atomix.raft.storage.system.MetaStore;
import io.camunda.debug.cli.sbe.SbeJsonDecoder;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "raft", description = "Print raft metadata files")
public class RaftCommand extends CommonOptions implements Callable<Integer> {

  @Spec CommandSpec spec;

  @Option(
      names = {"-f", "--file"},
      description = "Path of the file",
      required = true)
  private Path file;

  @Command(
      name = "metadata",
      description = "Print content of raft metastore (raft-partition-*.meta)")
  public int metadata() throws Exception {
    spec.commandLine().getOut().println(convertToJson());
    return 0;
  }

  @Command(
      name = "configuration",
      description = "Print content of raft configuration file (raft-partition-*.conf)")
  public int configuration() throws Exception {
    spec.commandLine().getOut().println(convertToJson());
    return 0;
  }

  private String convertToJson() throws Exception {
    if (verbose) {
      spec.commandLine().getErr().println("Reading file: " + file + "\n");
    }

    final var buffer = SbeJsonDecoder.readFile(file);
    final var version = buffer.getByte(0);
    if (version != MetaStore.VERSION) {
      throw new RuntimeException("Invalid version encoded in the metadata file: " + version);
    }

    final var ir = SbeJsonDecoder.loadIrFromResource(getClass(), "sbe/raft-entry-schema.sbeir");
    return SbeJsonDecoder.toJson(buffer, ir, MetaStore.VERSION_LENGTH);
  }

  @Override
  public Integer call() {
    spec.commandLine().usage(System.out);
    return 0;
  }
}
