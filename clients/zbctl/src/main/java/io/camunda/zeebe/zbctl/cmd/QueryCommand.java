/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.zbctl.cmd;

import io.camunda.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.zbctl.cmd.QueryCommand.ProcessDefinitionCommand;
import io.camunda.zeebe.zbctl.cmd.QueryCommand.ProcessInstanceCommand;
import io.camunda.zeebe.zbctl.mixin.ClientMixin;
import io.camunda.zeebe.zbctl.mixin.OutputMixin;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
    name = "query",
    description = "Query resources",
    subcommands = {ProcessDefinitionCommand.class, ProcessInstanceCommand.class})
public class QueryCommand {

  @Command(
      name = "definition",
      aliases = {"process-definition", "pd"},
      description = "Query process definitions")
  public static class ProcessDefinitionCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        // TODO filter, page support
        final var response = client.newProcessDefinitionQuery().send().join(30, TimeUnit.SECONDS);
        // outputMixin.formatter().write(response.page());
        outputMixin.formatter().write(response, SearchQueryResponse.class);
      }

      return 0;
    }
  }

  @Command(
      name = "instance",
      aliases = {"process-instance", "pi"},
      description = "Query process instances")
  public static class ProcessInstanceCommand implements Callable<Integer> {

    @Mixin private ClientMixin clientMixin;
    @Mixin private OutputMixin outputMixin;

    @Override
    public Integer call() throws Exception {
      try (final var client = clientMixin.client()) {
        // TODO filter, page support
        final var response = client.newProcessInstanceQuery().send().join(30, TimeUnit.SECONDS);
        // outputMixin.formatter().write(response.page());
        outputMixin.formatter().write(response, SearchQueryResponse.class);
      }

      return 0;
    }
  }
}
