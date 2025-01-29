/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.zbctl;

import io.camunda.zeebe.zbctl.cmd.ActivateCommand;
import io.camunda.zeebe.zbctl.cmd.AssignCommand;
import io.camunda.zeebe.zbctl.cmd.BroadcastCommand;
import io.camunda.zeebe.zbctl.cmd.CancelCommand;
import io.camunda.zeebe.zbctl.cmd.CompleteCommand;
import io.camunda.zeebe.zbctl.cmd.CreateCommand;
import io.camunda.zeebe.zbctl.cmd.DeleteCommand;
import io.camunda.zeebe.zbctl.cmd.DeployCommand;
import io.camunda.zeebe.zbctl.cmd.EvaluateCommand;
import io.camunda.zeebe.zbctl.cmd.FailCommand;
import io.camunda.zeebe.zbctl.cmd.PublishCommand;
import io.camunda.zeebe.zbctl.cmd.ResolveCommand;
import io.camunda.zeebe.zbctl.cmd.SetCommand;
import io.camunda.zeebe.zbctl.cmd.StatusCommand;
import io.camunda.zeebe.zbctl.cmd.ThrowErrorCommand;
import io.camunda.zeebe.zbctl.cmd.UnassignCommand;
import io.camunda.zeebe.zbctl.cmd.UpdateCommand;
import io.camunda.zeebe.zbctl.cmd.VersionCommand;
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
      VersionCommand.class
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
