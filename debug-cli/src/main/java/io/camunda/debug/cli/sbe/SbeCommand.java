/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.sbe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import uk.co.real_logic.sbe.ir.Ir;

@Command(name = "sbe", description = "Decode an SBE-encoded file using a provided schema")
public class SbeCommand implements Callable<Integer> {

  @Spec CommandSpec spec;

  @Option(
      names = {"-f", "--file"},
      description = "Path to the SBE-encoded file")
  private Path file;

  @Parameters(index = "0", arity = "0..1", description = "Path to the SBE-encoded file")
  private Path positionalFile;

  @Option(
      names = {"-s", "--schema"},
      description = "Path to the SBE schema (.xml or .sbeir)",
      required = true)
  private Path schema;

  @Option(
      names = {"-o", "--offset"},
      description = "Byte offset where the SBE message header starts",
      defaultValue = "0")
  private long offset;

  @Option(
      names = {"-v", "--verbose"},
      description = "Enable verbose output")
  private boolean verbose;

  @Override
  public Integer call() {
    try {
      final var inputFile = resolveInputFile();
      final var ir = resolveSchema();
      final var json = SbeJsonDecoder.toJson(inputFile, ir, offset);

      spec.commandLine().getOut().println(json);
      return 0;
    } catch (final Exception e) {
      e.printStackTrace(spec.commandLine().getErr());
      return 2;
    }
  }

  private Path resolveInputFile() {
    if (file != null && positionalFile != null) {
      throw new IllegalArgumentException(
          "Provide the input file either as a positional parameter or with --file");
    }

    final var resolvedFile = file != null ? file : positionalFile;
    if (resolvedFile == null) {
      throw new IllegalArgumentException(
          "Missing input file, provide it as a positional parameter or with --file");
    }

    if (verbose) {
      spec.commandLine().getErr().println("Reading file: " + resolvedFile);
      spec.commandLine().getErr().println("Offset used: " + offset);
    }

    return resolvedFile;
  }

  private Ir resolveSchema() throws Exception {
    if (Files.exists(schema)) {
      final var normalizedSchema = schema.toAbsolutePath().normalize();
      if (verbose) {
        spec.commandLine().getErr().println("Schema used: filesystem path " + normalizedSchema);
      }
      return SbeJsonDecoder.loadIr(schema);
    }

    if (verbose) {
      spec.commandLine().getErr().println("Schema used: classpath resource " + schema);
    }
    return SbeJsonDecoder.loadIrFromResource(getClass(), schema.toString());
  }
}
