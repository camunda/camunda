/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c8ctl.cmd;

import io.camunda.c8ctl.cmd.FailCommand.JobCommand;
import io.camunda.c8ctl.mixin.ClientMixin;
import io.camunda.c8ctl.mixin.OutputMixin;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.c8ctl.converters.JsonInputConverter;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "fail",
    description = "Fail a resource",
    subcommands = {JobCommand.class})
public class FailCommand {

  @Command(name = "job", description = "Fails a job with the given job key")
  public static class JobCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(paramLabel = "<job key>", description = "The job key", type = Long.class)
    private Long jobKey;

    @Option(
        names = {"--retries"},
        paramLabel = "<retries>",
        description = "Specify the retries left",
        type = Integer.class)
    private int retries;

    @Option(
        names = {"--errorMessage"},
        paramLabel = "<errorMessage>",
        description = "Specifies an error message for the failure",
        type = String.class)
    private String errorMessage;

    @Option(
        names = {"--variables"},
        paramLabel = "<variables>",
        description = "Specify an optional fail variables as JSON string or path to JSON file",
        defaultValue = "{}",
        converter = JsonInputConverter.class)
    private JsonInputConverter.JsonInput variables;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        final var command = prepareCommand(client);
        final var response = command.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, FailJobResponse.class);
      }
      return ExitCode.OK;
    }

    private FailJobCommandStep2 prepareCommand(final CamundaClient client) throws Exception {
      final var command =
          client
              .newFailCommand(jobKey)
              .retries(retries)
              .retryBackoff(Duration.of(3, ChronoUnit.SECONDS));

      if (errorMessage != null) {
        command.errorMessage(errorMessage);
      }
      try (final var variablesInput = variables.open()) {
        command.variables(variablesInput);
      }

      return command;
    }
  }
}
