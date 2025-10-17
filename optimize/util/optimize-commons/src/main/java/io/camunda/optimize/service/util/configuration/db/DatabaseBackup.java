/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class DatabaseBackup {

  @JsonProperty("repositoryName")
  private String snapshotRepositoryName;

  public DatabaseBackup() {}

  public String getSnapshotRepositoryName() {
    return snapshotRepositoryName;
  }

  @JsonProperty("repositoryName")
  public void setSnapshotRepositoryName(final String snapshotRepositoryName) {
    this.snapshotRepositoryName = snapshotRepositoryName;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DatabaseBackup;
  }

  @Override
  public int hashCode() {
    return Objects.hash(snapshotRepositoryName);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DatabaseBackup that = (DatabaseBackup) o;
    return Objects.equals(snapshotRepositoryName, that.snapshotRepositoryName);
  }

  @Override
  public String toString() {
    return "DatabaseBackup(snapshotRepositoryName=" + getSnapshotRepositoryName() + ")";
  }
}
