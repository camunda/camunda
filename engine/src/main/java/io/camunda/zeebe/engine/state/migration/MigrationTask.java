/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;

public interface MigrationTask {

  String getIdentifier();

  boolean needsToRun(final ZeebeState zeebeState);

  void runMigration(final MutableZeebeState zeebeState);
}

@interface MigrationMetadata {

  String fromVersion();

  String toVersion();
}
