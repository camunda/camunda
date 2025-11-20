/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli;

import io.atomix.raft.storage.serializer.MetaStoreSerializer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.agrona.concurrent.UnsafeBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import uk.co.real_logic.sbe.ir.IrDecoder;
import uk.co.real_logic.sbe.json.JsonPrinter;

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
      description = "Print content of raft configuration file (raft-partition-*.meta)")
  public int configuration() throws Exception {
    spec.commandLine().getOut().println(convertToJson());
    return 0;
  }

  private String convertToJson() throws Exception {
    if (verbose) {
      spec.commandLine().getErr().println("Reading file: " + file + "\n");
    }

    final var metadataContentBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
    try (final var fc = FileChannel.open(file, java.nio.file.StandardOpenOption.READ)) {
      fc.read(metadataContentBuffer, 0);
    }

    final UnsafeBuffer metadataWithoutVersion = new UnsafeBuffer();
    metadataWithoutVersion.wrap(
        metadataContentBuffer,
        MetaStoreSerializer.VERSION_LENGTH,
        metadataContentBuffer.capacity() - MetaStoreSerializer.VERSION_LENGTH);

    final byte[] irBytes;
    try (final var irFileResource =
        getClass().getClassLoader().getResourceAsStream("sbe/raft-entry-schema.sbeir")) {
      if (irFileResource == null) {
        throw new RuntimeException("Failed to get resource raft-entry-schema.sbeir");
      }

      irBytes = irFileResource.readAllBytes();
    }

    try (final IrDecoder irDecoder = new IrDecoder(ByteBuffer.wrap(irBytes))) {
      final var ir = irDecoder.decode();
      final StringBuilder output = new StringBuilder();
      new JsonPrinter(ir).print(output, metadataWithoutVersion, 0);
      return output.toString();
    }
  }

  @Override
  public Integer call() {
    spec.commandLine().usage(System.out);
    return 0;
  }
}
