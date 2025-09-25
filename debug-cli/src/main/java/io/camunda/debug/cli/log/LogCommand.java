/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.log;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;

@Command(
    name = "log",
    mixinStandardHelpOptions = true,
    subcommands = {LogPrintCommand.class},
    description = "Allows to inspect the log via sub commands")
public class LogCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  @Option(
      names = {"-p", "--path"},
      paramLabel = "LOG_PATH",
      description = "The path to the partition log data, should end with the partition id.",
      required = true,
      scope = ScopeType.INHERIT)
  private Path partitionPath;

  @Command(name = "status", description = "Print's the status of the log")
  public int status() throws Exception {
    System.out.println();
    try (final var logStatus = new LogStatus(partitionPath)) {
      final var status = logStatus.status();
      System.out.println(status);
    }
    return 0;
  }

  @Override
  public Integer call() {
    spec.commandLine().usage(System.out);
    return 0;
  }
}
