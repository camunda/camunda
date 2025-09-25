/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

/**
 * DeploymentDistributionEngineIntent is deprecated as of 8.3.0. A generalised way of distributing
 * commands has been introduced in this version. The DeploymentCreateProcessor is now using this new
 * way. This intent only remains to stay backwards compatible.
 */
@Deprecated
public enum DeploymentDistributionEngineIntent implements EngineIntent {
  DISTRIBUTING((short) 0),
  COMPLETE((short) 1),
  COMPLETED((short) 2);

  private final short value;

  DeploymentDistributionEngineIntent(final short value) {
    this.value = value;
  }

  public short getIntent() {
    return value;
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return DISTRIBUTING;
      case 1:
        return COMPLETE;
      case 2:
        return COMPLETED;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case COMPLETED:
      case DISTRIBUTING:
        return true;
      default:
        return false;
    }
  }
}
