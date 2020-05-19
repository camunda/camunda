/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.entity;

public final class Task {
  private String key;

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    this.key = key;
  }
}
