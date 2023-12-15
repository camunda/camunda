/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.cmd;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceWithResultCommandStep1;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.zbctl.cmd.CreateCommand.InstanceCommand;
import io.camunda.zeebe.zbctl.converters.JsonInputConverter;
import io.camunda.zeebe.zbctl.converters.JsonInputConverter.JsonInput;
import io.camunda.zeebe.zbctl.mixin.ClientMixin;
import io.camunda.zeebe.zbctl.mixin.OutputMixin;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "create",
    description = "Create resources",
    subcommands = {InstanceCommand.class})
public class CreateCommand {

  @Command(
      name = "instance",
      description = "Creates a new process instance defined by the process ID or process key")
  public static class InstanceCommand implements Callable<Integer> {
    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(
        paramLabel = "<processId or process key>",
        description = "Either the BPMN process ID or the process key")
    private String processId;

    @Option(
        names = {"--version"},
        paramLabel = "<version>",
        description = "Specify version of process which should be executed (use -1 for the latest)",
        defaultValue = "-1")
    private int version;

    @Option(
        names = {"--variables"},
        paramLabel = "<variables>",
        description = "Specify message variables as JSON string or path to JSON file",
        defaultValue = "{}",
        converter = JsonInputConverter.class)
    private JsonInput variables;

    @Option(
        names = {"--withResult"},
        paramLabel = "<withResult>",
        description =
            "Specify to await result of process, optional a list of variable names can be provided to limit the returned variables")
    private String[] withResult;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client();
          final var output = new BufferedOutputStream(System.out)) {
        var command = prepareCommand(client);

        try (final var variablesInput = variables.open()) {
          command = command.variables(variablesInput);
        }

        if (withResult != null) {
          sendCommandWithResult(command, output);
        } else {
          final var response = command.send().join(30, TimeUnit.SECONDS);
          outputMixin.formatter().write(output, response, ProcessInstanceEvent.class);
        }
      }

      return 0;
    }

    private void sendCommandWithResult(
        final CreateProcessInstanceCommandStep3 command, final BufferedOutputStream output)
        throws IOException {
      var commandWithResult = command.withResult();
      commandWithResult =
          Arrays.stream(withResult)
              .reduce(
                  commandWithResult,
                  CreateProcessInstanceWithResultCommandStep1::fetchVariables,
                  (c1, c2) -> c1);
      final var response = commandWithResult.send().join(30, TimeUnit.SECONDS);
      outputMixin.formatter().write(output, response, ProcessInstanceResult.class);
    }

    private CreateProcessInstanceCommandStep3 prepareCommand(final ZeebeClient client) {
      final var command = client.newCreateInstanceCommand();

      try {
        return command.processDefinitionKey(Integer.parseInt(processId));
      } catch (final NumberFormatException e) {
        return command.bpmnProcessId(processId).version(version);
      }
    }
  }
}
