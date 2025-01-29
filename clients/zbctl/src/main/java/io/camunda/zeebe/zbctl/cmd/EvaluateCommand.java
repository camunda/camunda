/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.zbctl.cmd;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.EvaluateDecisionCommandStep1.EvaluateDecisionCommandStep2;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.zeebe.zbctl.cmd.EvaluateCommand.DecisionCommand;
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
    name = "evaluate",
    description = "Evaluate resources",
    subcommands = {DecisionCommand.class})
public class EvaluateCommand {

  @Command(
      name = "decision",
      description = "Evaluates a decision defined by the decision ID or decision key")
  public static class DecisionCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(
        paramLabel = "<decisionId or decisionKey>",
        description = "The decision ID or decision key to evaluate",
        type = String.class)
    private String decisionParameter;

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
        var command = prepareCommand(client);

        try (final var variablesInput = variables.open()) {
          command = command.variables(variablesInput);
        }

        final var response = command.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, EvaluateDecisionResponse.class);
      }

      return 0;
    }

    private EvaluateDecisionCommandStep2 prepareCommand(final CamundaClient client) {

      final var command = client.newEvaluateDecisionCommand();
      try {
        return command.decisionKey(Integer.parseInt(decisionParameter));
      } catch (final NumberFormatException e) {
        return command.decisionId(decisionParameter);
      }
    }
  }
}
