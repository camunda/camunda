/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class PageSizeProperties {

  public static final int DEFAULT_PAGE_SIZE = 100;
  public static final int MAX_PAGE_SIZE = 10_000;

  @Min(1)
  @Max(MAX_PAGE_SIZE)
  private int users = DEFAULT_PAGE_SIZE;

  @Min(1)
  @Max(MAX_PAGE_SIZE)
  private int groups = DEFAULT_PAGE_SIZE;

  public int getUsers() {
    return users;
  }

  public void setUsers(final int users) {
    this.users = users;
  }

  public int getGroups() {
    return groups;
  }

  public void setGroups(final int groups) {
    this.groups = groups;
  }
}
