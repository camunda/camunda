/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.zbctl.cmd;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.DeleteDocumentCommandStep1;
import io.camunda.client.api.command.DeleteGroupCommandStep1;
import io.camunda.client.api.command.DeleteResourceCommandStep1;
import io.camunda.client.api.command.DeleteTenantCommandStep1;
import io.camunda.client.api.response.DeleteDocumentResponse;
import io.camunda.client.api.response.DeleteGroupResponse;
import io.camunda.client.api.response.DeleteResourceResponse;
import io.camunda.client.api.response.DeleteTenantResponse;
import io.camunda.zeebe.zbctl.cmd.DeleteCommand.DocumentCommand;
import io.camunda.zeebe.zbctl.cmd.DeleteCommand.GroupCommand;
import io.camunda.zeebe.zbctl.cmd.DeleteCommand.ResourceCommand;
import io.camunda.zeebe.zbctl.cmd.DeleteCommand.TenantCommand;
import io.camunda.zeebe.zbctl.mixin.ClientMixin;
import io.camunda.zeebe.zbctl.mixin.OutputMixin;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(
    name = "delete",
    description = "Delete resources",
    subcommands = {
      ResourceCommand.class,
      GroupCommand.class,
      TenantCommand.class,
      DocumentCommand.class
    })
public class DeleteCommand {

  @Command(name = "resource", description = "Deletes a resource defined by the resource key")
  public static class ResourceCommand implements Callable<Integer> {
    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(
        paramLabel = "<resource key>",
        description = "The key of the resource",
        type = Long.class)
    private long resourceKey;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        final var command = prepareCommand(client);

        final var response = command.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, DeleteResourceResponse.class);
      }

      return ExitCode.OK;
    }

    private DeleteResourceCommandStep1 prepareCommand(final CamundaClient client) {
      try {
        return client.newDeleteResourceCommand(resourceKey);
      } catch (final NumberFormatException e) {
        throw new IllegalArgumentException(
            "Expected a resource key, but got: %s".formatted(resourceKey), e);
      }
    }
  }

  @Command(name = "group", description = "Deletes a group defined by the group name")
  public static class GroupCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(paramLabel = "<group key>", description = "The group key", type = Long.class)
    private long groupKey;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        final var command = prepareCommand(client);

        final var response = command.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, DeleteGroupResponse.class);
      }

      return ExitCode.OK;
    }

    private DeleteGroupCommandStep1 prepareCommand(final CamundaClient client) {
      return client.newDeleteGroupCommand(groupKey);
    }
  }

  @Command(name = "tenant", description = "Deletes a tenant defined by the tenant name")
  public static class TenantCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(paramLabel = "<tenant key>", description = "The tenant key", type = Long.class)
    private long tenantKey;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        final var command = prepareCommand(client);

        final var response = command.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, DeleteTenantResponse.class);
      }

      return ExitCode.OK;
    }

    private DeleteTenantCommandStep1 prepareCommand(final CamundaClient client) {
      return client.newDeleteTenantCommand(tenantKey);
    }
  }

  @Command(name = "document", description = "Deletes a document defined by the document id")
  public static class DocumentCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Parameters(
        paramLabel = "<document id>",
        description = "The unique identifier of the document",
        type = String.class)
    private String documentId;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        final var command = prepareCommand(client);

        final var response = command.send().join(30, TimeUnit.SECONDS);
        outputMixin.formatter().write(response, DeleteDocumentResponse.class);
      }

      return ExitCode.OK;
    }

    private DeleteDocumentCommandStep1 prepareCommand(final CamundaClient client) {
      return client.newDeleteDocumentCommand(documentId);
    }
  }
}
