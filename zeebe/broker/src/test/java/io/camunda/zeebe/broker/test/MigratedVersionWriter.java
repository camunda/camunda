/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.test;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.api.EngineFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

/**
 * Records the version that last migrated the state by running the engine's real migrations as if
 * the broker was running that version.
 */
public final class MigratedVersionWriter {

  private MigratedVersionWriter() {}

  public static void writeMigratedByVersion(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final String version) {
    try {
      EngineFactory.runMigrations(1, zeebeDb, new EngineConfiguration(), false, () -> 1, version);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
