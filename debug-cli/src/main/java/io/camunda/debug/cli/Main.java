/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli;

import java.util.Optional;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "debug-cli",
    description = "Camunda Debug CLI - A tool for debugging and troubleshooting Camunda instances",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    subcommands = {CommandLine.HelpCommand.class, TopologyMetaCommand.class})
public class Main {

  @Option(
      names = {"-v", "--verbose"},
      description = "Enable verbose output")
  private boolean verbose;

  //  @Option(
  //      names = {"-c", "--config"},
  //      description = "Configuration file path",
  //      paramLabel = "<config-file>")
  //  private String configFile;

  @Option(
      names = {"--root"},
      description = "Path of the root of the data folder")
  private Optional<String> root;

  @Parameters(description = "Additional arguments")
  private String[] args;

  // Getter methods for subcommands to access parent options
  public boolean isVerbose() {
    return verbose;
  }

  public String[] getArgs() {
    return args;
  }

  public static void main(final String[] args) {
    final int exitCode = new CommandLine(new Main()).execute(args);
    System.exit(exitCode);
  }
}
