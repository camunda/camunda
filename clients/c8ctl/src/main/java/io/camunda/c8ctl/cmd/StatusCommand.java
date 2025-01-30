/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c8ctl.cmd;

import io.camunda.c8ctl.mixin.ClientMixin;
import io.camunda.c8ctl.mixin.OutputMixin;
import io.camunda.client.api.response.Topology;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;

@Command(name = "status", description = "Checks the current status of the cluster")
public final class StatusCommand implements Callable<Integer> {
  @Mixin private ClientMixin clientMixin;
  @Mixin private OutputMixin outputMixin;

  @Override
  public Integer call() throws Exception {
    try (final var client = clientMixin.client()) {
      final var topology = client.newTopologyRequest().send().join();
      outputMixin.formatter().write(topology, Topology.class);
    }

    return ExitCode.OK;
  }
}
