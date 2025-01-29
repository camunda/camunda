/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.zbctl.cmd;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.BroadcastSignalCommandStep1.BroadcastSignalCommandStep2;
import io.camunda.client.api.response.BroadcastSignalResponse;
import io.camunda.zeebe.zbctl.cmd.BroadcastCommand.SignalCommand;
import io.camunda.zeebe.zbctl.converters.JsonInputConverter;
import io.camunda.zeebe.zbctl.converters.JsonInputConverter.JsonInput;
import io.camunda.zeebe.zbctl.mixin.ClientMixin;
import io.camunda.zeebe.zbctl.mixin.OutputMixin;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "broadcast",
    description = "Broadcast a signal",
    subcommands = {SignalCommand.class})
public class BroadcastCommand {

  @Command(name = "signal", description = "Broadcast a signal by name")
  public static class SignalCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(paramLabel = "<signalName>", description = "The name of the signal to broadcast")
    private String signalName;

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

        final Map<String, Object> variablesInput = variables.get();
        if (variablesInput != null && !variablesInput.isEmpty()) {
          command.variables(variablesInput);
        }

        final var response = command.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, BroadcastSignalResponse.class);
      }

      return ExitCode.OK;
    }

    private BroadcastSignalCommandStep2 prepareCommand(final CamundaClient client) {
      return client.newBroadcastSignalCommand().signalName(signalName);
    }
  }
}
