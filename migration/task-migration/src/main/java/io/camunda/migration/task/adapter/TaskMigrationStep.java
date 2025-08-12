/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.adapter;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/* Fork of Operate's migration entity */
public class TaskMigrationStep {

  @JsonProperty("@type")
  private final String type = "taskMigrationStep";

  private String content;

  private String description;

  private String indexName;

  private String version;

  private OffsetDateTime appliedDate;

  private boolean applied;

  public String getType() {
    return type;
  }

  public String getContent() {
    return content;
  }

  public void setContent(final String content) {
    this.content = content;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public String getIndexName() {
    return indexName;
  }

  public void setIndexName(final String indexName) {
    this.indexName = indexName;
  }

  public OffsetDateTime getAppliedDate() {
    return appliedDate;
  }

  public void setAppliedDate(final OffsetDateTime appliedDate) {
    this.appliedDate = appliedDate;
  }

  public boolean isApplied() {
    return applied;
  }

  public void setApplied(final boolean applied) {
    this.applied = applied;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(final String version) {
    this.version = version;
  }
}
