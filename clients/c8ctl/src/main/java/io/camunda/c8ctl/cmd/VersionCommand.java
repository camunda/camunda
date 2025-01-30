/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c8ctl.cmd;

import io.camunda.c8ctl.mixin.OutputMixin;
import io.camunda.client.impl.util.VersionUtil;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(name = "version", description = "Print the version of c8ctl")
public class VersionCommand implements Callable<Integer> {

  @Mixin private OutputMixin outputMixin;

  @Override
  public Integer call() throws Exception {
    outputMixin
        .formatter()
        .write("c8ctl %s (commit: %s)".formatted(VersionUtil.getVersion(), "TODO"), String.class);
    return 0;
  }
}
