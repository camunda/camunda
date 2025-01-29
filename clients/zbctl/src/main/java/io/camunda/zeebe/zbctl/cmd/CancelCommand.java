/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.zbctl.cmd;

import io.camunda.client.api.response.CancelProcessInstanceResponse;
import io.camunda.zeebe.zbctl.cmd.CancelCommand.ProcessInstanceCommand;
import io.camunda.zeebe.zbctl.mixin.ClientMixin;
import io.camunda.zeebe.zbctl.mixin.OutputMixin;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(
    name = "cancel",
    description = "Cancel resource",
    subcommands = {ProcessInstanceCommand.class})
public class CancelCommand {

  @Command(
      name = "process-instance",
      aliases = {"pi"},
      description = "Cancel a process instance by key")
  public static class ProcessInstanceCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(
        paramLabel = "<processInstanceKey>",
        description = "The process instance key to cancel",
        type = Long.class)
    private long processInstanceKey;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {

        final var response =
            client.newCancelInstanceCommand(processInstanceKey).send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, CancelProcessInstanceResponse.class);
      }

      return 0;
    }
  }
}
