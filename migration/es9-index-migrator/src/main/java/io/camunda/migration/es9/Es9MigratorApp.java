/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.es9;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParameterException;

/**
 * Entrypoint of the ES9 index migrator Job. Modes: {@code prepare}, {@code migrate
 * --group=oc|optimize} and {@code recover}. The exit code is the contract with the operator, see
 * {@link ExitCode}.
 */
@Command(
    name = "es9-migrator",
    mixinStandardHelpOptions = true,
    description = "Rewrites Elasticsearch indices created before ES 8 so that ES 9 can open them.",
    subcommands = {PrepareCommand.class, MigrateCommand.class, RecoverCommand.class})
public final class Es9MigratorApp {

  /** Picocli's own code for a bad command line; outside {@link ExitCode}, so Kubernetes retries. */
  static final int USAGE_ERROR = 2;

  private static final Logger LOG = LoggerFactory.getLogger(Es9MigratorApp.class);

  private Es9MigratorApp() {}

  public static void main(final String[] args) {
    System.exit(execute(args));
  }

  static int execute(final String... args) {
    final var commandLine = new CommandLine(new Es9MigratorApp());
    final Mode mode;
    try {
      final var parseResult = commandLine.parseArgs(args);
      if (CommandLine.printHelpIfRequested(parseResult)) {
        return ExitCode.DONE.code();
      }
      final var subcommand = parseResult.subcommand();
      if (subcommand == null) {
        throw new ParameterException(commandLine, "Missing required subcommand");
      }
      mode = (Mode) subcommand.commandSpec().userObject();
    } catch (final ParameterException e) {
      LOG.error("Invalid arguments: {}", e.getMessage());
      e.getCommandLine().usage(System.err);
      return USAGE_ERROR;
    }

    return execute(mode);
  }

  static int execute(final Mode mode) {
    try {
      return exitCodeOf(mode.run());
    } catch (final Exception e) {
      LOG.error(
          "Unexpected error, exiting with {} so that it is retried", ExitCode.UNEXPECTED_ERROR, e);
      return ExitCode.UNEXPECTED_ERROR;
    }
  }

  static int exitCodeOf(final ModeResult result) {
    final var exitCode = ExitCode.of(result);
    switch (result) {
      case ModeResult.NeedsHumanClean(final var reason) ->
          LOG.error("Exiting with {}: {}", exitCode.code(), reason);
      case ModeResult.NeedsHumanUnclean(final var reason) ->
          LOG.error("Exiting with {}: {}", exitCode.code(), reason);
      case ModeResult.Done ignored -> {}
    }
    return exitCode.code();
  }
}
