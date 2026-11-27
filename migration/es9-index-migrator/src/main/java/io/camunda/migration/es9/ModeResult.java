/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.es9;

/** What a {@link Mode} reports to {@link Es9MigratorApp}, which alone maps it to an exit code. */
public sealed interface ModeResult {

  /** Everything the mode was asked to do is finished. */
  record Done() implements ModeResult {}

  /** A human is needed and the state is clean: the Job cleaned up, the apps can start again. */
  record NeedsHumanClean(String reason) implements ModeResult {}

  /** A human is needed and the state is not clean: retrying will not help. */
  record NeedsHumanUnclean(String reason) implements ModeResult {}
}
