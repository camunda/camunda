/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.db.ConsistencyChecksSettings;

public class ConsistencyCheckCfg {
  private static final boolean DEFAULT_ENABLE_PRECONDITIONS = false;
  private static final boolean DEFAULT_ENABLE_FOREIGN_KEY_CHECKS = false;
  private boolean enablePreconditions = DEFAULT_ENABLE_PRECONDITIONS;
  private boolean enableForeignKeyChecks = DEFAULT_ENABLE_FOREIGN_KEY_CHECKS;

  public boolean isEnablePreconditions() {
    return enablePreconditions;
  }

  public void setEnablePreconditions(final boolean enablePreconditions) {
    this.enablePreconditions = enablePreconditions;
  }

  public boolean isEnableForeignKeyChecks() {
    return enableForeignKeyChecks;
  }

  public void setEnableForeignKeyChecks(final boolean enableForeignKeyChecks) {
    this.enableForeignKeyChecks = enableForeignKeyChecks;
  }

  public ConsistencyChecksSettings getSettings() {
    return new ConsistencyChecksSettings(enablePreconditions, enableForeignKeyChecks);
  }

  @Override
  public String toString() {
    return "ConsistencyCheckCfg{" + "enablePreconditions=" + enablePreconditions + '}';
  }
}
