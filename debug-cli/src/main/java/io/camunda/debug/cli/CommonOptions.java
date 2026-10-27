/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli;

import java.nio.file.Path;
import picocli.CommandLine.Option;

abstract class CommonOptions {

  @Option(
      names = {"-r", "--root"},
      description = "Path of the root of the data folder")
  protected Path root;

  @Option(
      names = {"-v", "--verbose"},
      description = "Enable verbose output")
  protected boolean verbose;
}
