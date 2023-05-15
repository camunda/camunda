/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.cmd;

import io.camunda.zeebe.zbctl.mixin.ClientMixin;
import io.camunda.zeebe.zbctl.mixin.OutputMixin;
import java.io.BufferedOutputStream;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(name = "status", description = "Checks the current status of the cluster")
public final class StatusCommand implements Callable<Integer> {
  @Mixin private ClientMixin clientMixin;
  @Mixin private OutputMixin outputMixin;

  @Override
  public Integer call() throws Exception {
    try (final var client = clientMixin.client();
        final var output = new BufferedOutputStream(System.out)) {
      final var topology = client.newTopologyRequest().send().join();
      outputMixin.formatter().write(output, topology);
    }

    return 0;
  }
}
