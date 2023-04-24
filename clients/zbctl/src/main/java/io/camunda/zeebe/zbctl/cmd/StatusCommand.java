/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.cmd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.zbctl.mixin.ClientMixin;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(name = "status", description = "Checks the current status of the cluster")
public final class StatusCommand implements Callable<String> {
  private static final ObjectWriter JSON_WRITER =
      new ObjectMapper().writerWithDefaultPrettyPrinter().forType(Topology.class);

  @Mixin private ClientMixin clientMixin;

  @Override
  public String call() throws Exception {
    try (final var client = clientMixin.client()) {
      final var topology = client.newTopologyRequest().send().join();
      return JSON_WRITER.writeValueAsString(topology);
    }
  }
}
