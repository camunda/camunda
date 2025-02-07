/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c8ctl.cmd;

import io.camunda.c8ctl.cmd.CompleteCommand.JobCommand;
import io.camunda.c8ctl.cmd.CompleteCommand.UserTaskCommand;
import io.camunda.c8ctl.converters.JsonInputConverter;
import io.camunda.c8ctl.converters.JsonInputConverter.JsonInput;
import io.camunda.c8ctl.mixin.ClientMixin;
import io.camunda.c8ctl.mixin.OutputMixin;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CompleteUserTaskCommandStep1;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.response.CompleteUserTaskResponse;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "complete",
    description = "Complete actions",
    subcommands = {UserTaskCommand.class, JobCommand.class})
public class CompleteCommand {

  @Command(
      name = "userTask",
      aliases = {"user-task", "ut"},
      description = "Completes a user task defined by the user task key")
  public static class UserTaskCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(
        paramLabel = "<user task key>",
        description = "The key of the user task",
        type = Long.class)
    private long userTaskKey;

    @Option(
        names = {"--action"},
        paramLabel = "<action>",
        description = "The action to complete the user task with",
        defaultValue = "")
    private String action;

    @Option(
        names = {"--variables"},
        paramLabel = "<variables>",
        description = "Specify message variables as JSON string or path to JSON file",
        defaultValue = "{}",
        converter = JsonInputConverter.class)
    private JsonInput variables;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        final var command = prepareCommand(client);

        if (action != null && !action.isEmpty()) {
          command.action(action);
        }

        final Map<String, Object> variablesInput = variables.get();
        if (variablesInput != null && !variablesInput.isEmpty()) {
          command.variables(variablesInput);
        }

        final var response = command.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, CompleteUserTaskResponse.class);
      }

      return ExitCode.OK;
    }

    private CompleteUserTaskCommandStep1 prepareCommand(final CamundaClient client) {
      return client.newUserTaskCompleteCommand(userTaskKey);
    }
  }

  @Command(name = "job", description = "Completes a job defined by the job key")
  public static class JobCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(paramLabel = "<job key>", description = "The key of the job", type = Long.class)
    private long jobKey;

    @Option(
        names = {"--variables"},
        paramLabel = "<variables>",
        description = "Specify job variables as JSON string or path to JSON file",
        defaultValue = "{}",
        converter = JsonInputConverter.class)
    private JsonInput variables;

    // TODO: implement withResult
    //    @Option(
    //        names = {"--withResult"},
    //        paramLabel = "<withResult>",
    //        description =
    //            "Specify to await result of process, optional a list of variable names can be
    // provided to limit the returned variables")
    //    private String[] withResult;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        final var command = client.newCompleteCommand(jobKey);

        try (final var variablesInput = variables.open()) {
          command.variables(variablesInput);
        }

        final var response = command.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, CompleteJobResponse.class);
      }

      return ExitCode.OK;
    }
  }
}
