/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.cmd;

import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.zbctl.cmd.PublishCommand.MessageCommand;
import io.camunda.zeebe.zbctl.converters.DurationConverter;
import io.camunda.zeebe.zbctl.converters.JsonInputConverter;
import io.camunda.zeebe.zbctl.converters.JsonInputConverter.JsonInput;
import io.camunda.zeebe.zbctl.mixin.ClientMixin;
import io.camunda.zeebe.zbctl.mixin.OutputMixin;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "publish",
    description = "Publish a message",
    subcommands = {MessageCommand.class})
public class PublishCommand {

  @Command(name = "message", description = "Publish a message by name and correlation key")
  public static class MessageCommand implements Callable<Integer> {
    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(paramLabel = "<messageName>", description = "The name of the message")
    private String messageName;

    @Option(
        names = {"--correlationKey"},
        paramLabel = "<correlationKey>",
        description = "Specify message correlation key",
        required = true)
    private String correlationKey;

    @Option(
        names = {"--messageId"},
        paramLabel = "<messageId>",
        description = "Specify the unique ID of the message")
    private String messageId;

    @Option(
        names = {"--ttl"},
        paramLabel = "<ttl>",
        description = "Specify the time to live of the message. Example values: 300ms, 50s or 1m",
        defaultValue = "5s",
        converter = DurationConverter.class)
    private Duration ttl;

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
        final var command =
            client
                .newPublishMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey)
                .timeToLive(ttl);
        if (messageId != null && !messageId.isBlank()) {
          command.messageId(messageId);
        }

        try (final var variablesInput = variables.open()) {
          command.variables(variablesInput);
        }

        final var response = command.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, PublishMessageResponse.class);
      }

      return ExitCode.OK;
    }
  }
}
