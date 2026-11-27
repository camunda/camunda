/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.es9;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;

@Command(
    mixinStandardHelpOptions = true,
    name = "recover",
    description = "Brings a failed migration back to a clean state.")
final class RecoverCommand implements Mode {

  private static final Logger LOG = LoggerFactory.getLogger(RecoverCommand.class);

  @Override
  public ModeResult run() {
    LOG.info("recover: not implemented yet, nothing to do");
    return new ModeResult.Done();
  }
}
