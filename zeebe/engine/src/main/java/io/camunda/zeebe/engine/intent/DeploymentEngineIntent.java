/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum DeploymentEngineIntent implements EngineIntent {
  CREATE((short) 0),
  CREATED((short) 1),

  /**
   * EngineIntent related to distribution are deprecated as of 8.3.0. A generalised way of distributing
   * commands has been introduced in this version. The DeploymentCreateProcessor is now using this
   * new way. This intent only remains to stay backwards compatible.
   */
  @Deprecated
  DISTRIBUTE((short) 2),
  @Deprecated
  DISTRIBUTED((short) 3),
  @Deprecated
  FULLY_DISTRIBUTED((short) 4),

  RECONSTRUCT((short) 5),
  RECONSTRUCTED((short) 6),
  RECONSTRUCTED_ALL((short) 7);

  private final short value;

  DeploymentEngineIntent(final short value) {
    this.value = value;
  }

  public short getIntent() {
    return value;
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return DISTRIBUTE;
      case 3:
        return DISTRIBUTED;
      case 4:
        return FULLY_DISTRIBUTED;
      case 5:
        return RECONSTRUCT;
      case 6:
        return RECONSTRUCTED;
      case 7:
        return RECONSTRUCTED_ALL;
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
      case CREATED:
      case DISTRIBUTED:
      case FULLY_DISTRIBUTED:
      case RECONSTRUCTED:
      case RECONSTRUCTED_ALL:
        return true;
      default:
        return false;
    }
  }
}
