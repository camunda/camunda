/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.es9;

import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    mixinStandardHelpOptions = true,
    name = "prepare",
    description = "Detects what has to be migrated; changes no data.")
final class PrepareCommand implements Mode {

  private static final Logger LOG = LoggerFactory.getLogger(PrepareCommand.class);

  @Option(
      names = "--termination-log",
      description = "Where the termination message is written (default: ${DEFAULT-VALUE})")
  private Path terminationLog = Path.of("/dev/termination-log");

  @Override
  public ModeResult run() throws Exception {
    LOG.info("prepare: not implemented yet, nothing to migrate");
    TerminationMessage.write(terminationLog, List.of());
    return new ModeResult.Done();
  }
}
