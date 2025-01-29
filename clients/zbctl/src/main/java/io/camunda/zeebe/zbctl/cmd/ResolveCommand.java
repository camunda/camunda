/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.zbctl.cmd;

import io.camunda.client.api.response.ResolveIncidentResponse;
import io.camunda.zeebe.zbctl.cmd.ResolveCommand.IncidentCommand;
import io.camunda.zeebe.zbctl.mixin.ClientMixin;
import io.camunda.zeebe.zbctl.mixin.OutputMixin;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(
    name = "resolve",
    description = "Resolve resources",
    subcommands = {IncidentCommand.class})
public class ResolveCommand {

  @Command(name = "incident", description = "Resolve an existing incident of a process instance")
  public static class IncidentCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(
        paramLabel = "<key>",
        description = "The incident key to resolve",
        type = Long.class)
    private long incidentKey;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        final var response =
            client.newResolveIncidentCommand(incidentKey).send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, ResolveIncidentResponse.class);
      }

      return 0;
    }
  }
}
