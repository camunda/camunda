/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.zbctl.cmd;

import io.camunda.client.api.response.UnassignUserTaskResponse;
import io.camunda.zeebe.zbctl.mixin.ClientMixin;
import io.camunda.zeebe.zbctl.mixin.OutputMixin;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(
    name = "unassign",
    description = "Unassign a task",
    subcommands = {UnassignCommand.UserTaskCommand.class})
public class UnassignCommand {

  @Command(name = "userTask", description = "Unassign a user task")
  public static class UserTaskCommand implements Callable<Integer> {
    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(
        paramLabel = "<user task key>",
        description = "The key of the user task",
        type = Long.class)
    private long userTaskKey;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        final var command = client.newUserTaskUnassignCommand(userTaskKey);
        final var response = command.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, UnassignUserTaskResponse.class);
      }
      return ExitCode.OK;
    }
  }
}
