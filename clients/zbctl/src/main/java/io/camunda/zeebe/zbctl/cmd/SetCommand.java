/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.zbctl.cmd;

import io.camunda.client.api.command.SetVariablesCommandStep1.SetVariablesCommandStep2;
import io.camunda.client.api.response.SetVariablesResponse;
import io.camunda.zeebe.zbctl.cmd.SetCommand.VariablesCommand;
import io.camunda.zeebe.zbctl.converters.JsonInputConverter;
import io.camunda.zeebe.zbctl.converters.JsonInputConverter.JsonInput;
import io.camunda.zeebe.zbctl.mixin.ClientMixin;
import io.camunda.zeebe.zbctl.mixin.OutputMixin;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "set",
    description = "Set resource",
    subcommands = {VariablesCommand.class})
public class SetCommand {

  @Command(name = "variables", description = "Sets the variables of a given flow element")
  public static class VariablesCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(
        paramLabel = "<key>",
        description = "The key of the flow element",
        type = Long.class)
    private long elementInstanceKey;

    @Option(
        names = {"--variables"},
        paramLabel = "<variables>",
        description = "Specify variables as JSON string (default \"{}\")",
        defaultValue = "{}",
        converter = JsonInputConverter.class)
    private JsonInput variables;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {

        final var step1 = client.newSetVariablesCommand(elementInstanceKey);
        final SetVariablesCommandStep2 step2;
        try (final var variablesInput = variables.open()) {
          step2 = step1.variables(variablesInput);
        }
        final var response = step2.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, SetVariablesResponse.class);
      }

      return 0;
    }
  }
}
