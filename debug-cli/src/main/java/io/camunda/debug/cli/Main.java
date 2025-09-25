/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "debug-cli",
    description = "Camunda Debug CLI - A tool for debugging and troubleshooting Camunda instances",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    subcommands = {CommandLine.HelpCommand.class, TopologyMetaCommand.class})
public class Main {

  public static void main(final String[] args) {
    try {
      final var exitCode = new CommandLine(new Main()).execute(args);
      System.exit(exitCode);
    } catch (final Exception e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }
}
