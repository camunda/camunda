/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

/** Entity mapping for the AUTH_GROUP table. */
public class GroupEntity {

  private long groupKey;
  private String groupId;
  private String name;
  private String description;

  public long getGroupKey() {
    return groupKey;
  }

  public void setGroupKey(final long groupKey) {
    this.groupKey = groupKey;
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(final String groupId) {
    this.groupId = groupId;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }
}
