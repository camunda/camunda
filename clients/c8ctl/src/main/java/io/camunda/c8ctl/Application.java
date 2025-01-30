/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c8ctl;

import io.camunda.c8ctl.cmd.ActivateCommand;
import io.camunda.c8ctl.cmd.AssignCommand;
import io.camunda.c8ctl.cmd.BroadcastCommand;
import io.camunda.c8ctl.cmd.CancelCommand;
import io.camunda.c8ctl.cmd.CompleteCommand;
import io.camunda.c8ctl.cmd.CreateCommand;
import io.camunda.c8ctl.cmd.DeleteCommand;
import io.camunda.c8ctl.cmd.DeployCommand;
import io.camunda.c8ctl.cmd.EvaluateCommand;
import io.camunda.c8ctl.cmd.FailCommand;
import io.camunda.c8ctl.cmd.PublishCommand;
import io.camunda.c8ctl.cmd.QueryCommand;
import io.camunda.c8ctl.cmd.ResolveCommand;
import io.camunda.c8ctl.cmd.SetCommand;
import io.camunda.c8ctl.cmd.StatusCommand;
import io.camunda.c8ctl.cmd.ThrowErrorCommand;
import io.camunda.c8ctl.cmd.UnassignCommand;
import io.camunda.c8ctl.cmd.UpdateCommand;
import io.camunda.c8ctl.cmd.VersionCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

@Command(
    name = "zbctl",
    exitCodeListHeading = "Exit codes:%n",
    exitCodeList = {"0: Successful exit", "1: Application error"},
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    description = {
      "zbctl is a command line interface designed to create and read resources inside a zeebe broker.",
      "It is designed for regular maintenance jobs such as:",
      "\t* deploying processes",
      "\t* creating jobs and process instances",
      "\t* activating, completing or failing jobs",
      "\t* update variables and retries",
      "\t* view cluster status"
    },
    usageHelpAutoWidth = true,
    subcommands = {
      ActivateCommand.class,
      AssignCommand.class,
      BroadcastCommand.class,
      CancelCommand.class,
      CompleteCommand.class,
      CreateCommand.class,
      DeleteCommand.class,
      DeployCommand.class,
      EvaluateCommand.class,
      FailCommand.class,
      HelpCommand.class,
      PublishCommand.class,
      ResolveCommand.class,
      SetCommand.class,
      StatusCommand.class,
      ThrowErrorCommand.class,
      UnassignCommand.class,
      UpdateCommand.class,
      VersionCommand.class,
      QueryCommand.class,
    })
public final class Application {

  public static void main(final String... args) {
    final var exitCode =
        new CommandLine(new Application())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .setExecutionExceptionHandler(new ExceptionHandler())
            .execute(args);
    System.exit(exitCode);
  }
}
