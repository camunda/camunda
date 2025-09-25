/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum UserEngineIntent implements EngineIntent {
  CREATE(0),
  CREATED(1),
  UPDATE(2),
  UPDATED(3),
  DELETE(4),
  DELETED(5),
  CREATE_INITIAL_ADMIN(6),
  INITIAL_ADMIN_CREATED(7);

  private final short value;

  UserEngineIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case CREATED:
      case UPDATED:
      case DELETED:
      case INITIAL_ADMIN_CREATED:
        return true;
      default:
        return false;
    }
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return UPDATE;
      case 3:
        return UPDATED;
      case 4:
        return DELETE;
      case 5:
        return DELETED;
      case 6:
        return CREATE_INITIAL_ADMIN;
      case 7:
        return INITIAL_ADMIN_CREATED;
      default:
        return UNKNOWN;
    }
  }
}
