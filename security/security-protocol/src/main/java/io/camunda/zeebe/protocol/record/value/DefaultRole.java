/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.value;

public enum DefaultRole {
  ADMIN("admin"),
  RPA("rpa"),
  CONNECTORS("connectors"),
  APP_INTEGRATIONS("app-integrations"),
  TASK_WORKER("task-worker");

  private final String id;

  DefaultRole(final String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }
}
