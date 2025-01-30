/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.zbctl.cmd;

import io.camunda.client.api.response.AssignUserTaskResponse;
import io.camunda.zeebe.zbctl.converters.JsonInputConverter;
import io.camunda.zeebe.zbctl.mixin.ClientMixin;
import io.camunda.zeebe.zbctl.mixin.OutputMixin;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "assign", description = "Assign a user task")
public class AssignCommand {

  @Command(name = "userTask", description = "Assign a user task")
  public static class UserTask implements Callable<Integer> {
    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(
        paramLabel = "<user task key>",
        description = "The key of the user task",
        type = Long.class)
    private long userTaskKey;

    @Option(
        names = {"--assignee"},
        paramLabel = "<assignee>",
        description = "The assignee for the user task",
        converter = JsonInputConverter.class)
    private String assignee;

    @Option(
        names = {"--allowOverride"},
        paramLabel = "<allow override>",
        description = "Allow overriding the assigned user",
        defaultValue = "false",
        converter = JsonInputConverter.class)
    private boolean allowOverride;

    @Option(
        names = {"--action"},
        paramLabel = "<action>",
        description = "A custom action value that will be accessible from user task events",
        defaultValue = "assign")
    private String action;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        final var command = client.newUserTaskAssignCommand(userTaskKey).assignee(assignee);
        if (allowOverride) {
          command.allowOverride(true);
        }
        if (action != null) {
          command.action(action);
        }
        final var response = command.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, AssignUserTaskResponse.class);
      }
      return ExitCode.OK;
    }
  }
}
