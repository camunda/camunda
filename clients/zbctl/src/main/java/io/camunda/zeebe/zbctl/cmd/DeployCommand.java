/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.cmd;

import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.zbctl.cmd.DeployCommand.ResourceCommand;
import io.camunda.zeebe.zbctl.converters.PathConverter;
import io.camunda.zeebe.zbctl.mixin.ClientMixin;
import io.camunda.zeebe.zbctl.mixin.OutputMixin;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "deploy",
    description = "Deploy a resource",
    subcommands = {ResourceCommand.class})
public class DeployCommand {

  private static final List<String> EMPTY_STRING_LIST = List.of();

  @Command(name = "resource", description = "Deploy a resource")
  public static class ResourceCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(
        paramLabel = "<resourcePath>",
        description = "The path of the resource",
        converter = PathConverter.class)
    private List<Path> resourcePaths;

    @Option(
        names = "--resourceNames",
        description =
            "Resource names for the resource paths passed as arguments. The resource names are matched to resources by position. If a resource does not have a matching resource name, the resource path is used instead",
        paramLabel = "<resourceNames>",
        defaultValue = Option.NULL_VALUE)
    private String[] resourceNames;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client();
          final var output = new BufferedOutputStream(System.out)) {

        final var commandStep1 = client.newDeployResourceCommand();
        String resourceName =
            resourceNames != null && resourceNames.length > 0
                ? resourceNames[0]
                : resourcePaths.get(0).getFileName().toString();
        final var command =
            commandStep1.addResourceString(
                getResourceString(resourcePaths.get(0)), StandardCharsets.UTF_8, resourceName);
        for (int i = 1; i < resourcePaths.size(); i++) {
          resourceName =
              resourceNames != null && resourceNames.length < i
                  ? resourceNames[i]
                  : resourcePaths.get(i).getFileName().toString();
          commandStep1.addResourceString(
              getResourceString(resourcePaths.get(i)), StandardCharsets.UTF_8, resourceName);
        }

        final var response = command.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(output, response, DeploymentEvent.class);
      }

      return 0;
    }

    private String getResourceString(final Path resourcePath) throws IOException {
      return Files.readString(resourcePath);
    }
  }
}
