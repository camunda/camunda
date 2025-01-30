/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c8ctl.cmd;

import io.camunda.c8ctl.cmd.ActivateCommand.JobsCommand;
import io.camunda.c8ctl.converters.DurationConverter;
import io.camunda.c8ctl.mixin.ClientMixin;
import io.camunda.c8ctl.mixin.OutputMixin;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ActivateJobsCommandStep1.ActivateJobsCommandStep3;
import io.camunda.client.api.response.ActivateJobsResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "activate",
    description = "Activate jobs for type",
    subcommands = {
      JobsCommand.class,
    })
public class ActivateCommand {

  @Command(name = "jobs", description = "Activate jobs for type")
  public static class JobsCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(paramLabel = "<type>", description = "The job type")
    private String jobType;

    @Option(
        names = {"--maxJobsToActivate"},
        paramLabel = "<maxJobsToActivate>",
        description = "Specify the maximum number of jobs to activate",
        defaultValue = "1")
    private int maxJobsToActivate;

    @Option(
        names = {"--worker"},
        paramLabel = "<worker>",
        description = "Specify the name of the worker",
        defaultValue = "c8ctl")
    private String worker;

    @Option(
        names = {"--variables"},
        paramLabel = "<variables>",
        description =
            "Specify the list of variable names which should be fetch on job activation (comma-separated)",
        split = ",")
    private List<String> variables;

    @Option(
        names = {"--timeout"},
        paramLabel = "<timeout>",
        description =
            "Specify the timeout of the activated job. Example values: 300ms, 50s or 1m (default 5m0s)",
        defaultValue = "5m",
        converter = DurationConverter.class)
    private Duration timeout;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        final var command = prepareCommand(client);
        final var response = command.send().join();
        outputMixin.formatter().write(response, ActivateJobsResponse.class);
      }

      return ExitCode.OK;
    }

    private ActivateJobsCommandStep3 prepareCommand(final CamundaClient client) {
      final var cmd =
          client
              .newActivateJobsCommand()
              .jobType(jobType)
              .maxJobsToActivate(maxJobsToActivate)
              .workerName(worker)
              .timeout(timeout);
      if (variables != null) {
        cmd.fetchVariables(variables);
      }
      return cmd;
    }
  }
}
