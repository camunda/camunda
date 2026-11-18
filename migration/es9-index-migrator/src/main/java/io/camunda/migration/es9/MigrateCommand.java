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
import picocli.CommandLine.Option;

@Command(
    mixinStandardHelpOptions = true,
    name = "migrate",
    description = "Rewrites the pre-8 indices of one group.")
final class MigrateCommand implements Mode {

  private static final Logger LOG = LoggerFactory.getLogger(MigrateCommand.class);

  @Option(
      names = "--group",
      required = true,
      description = "Index group to migrate: oc or optimize",
      converter = GroupConverter.class)
  private Group group;

  @Override
  public ModeResult run() {
    LOG.info("migrate --group={}: not implemented yet, nothing to do", group.id());
    return new ModeResult.Done();
  }
}
