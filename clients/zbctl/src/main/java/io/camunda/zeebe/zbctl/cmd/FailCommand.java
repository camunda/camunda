/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.zbctl.cmd;

import io.camunda.client.api.response.FailJobResponse;
import io.camunda.zeebe.zbctl.cmd.FailCommand.JobCommand;
import io.camunda.zeebe.zbctl.mixin.ClientMixin;
import io.camunda.zeebe.zbctl.mixin.OutputMixin;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;
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

    @Parameters(paramLabel = "<job key>", description = "The job key")
    private String jobKey;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        final var command =
            client
                .newFailCommand(Integer.parseInt(jobKey))
                .retries(3)
                .retryBackoff(Duration.of(3, ChronoUnit.SECONDS));
        final var response = command.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, FailJobResponse.class);
      }
      return ExitCode.OK;
    }
  }
}
