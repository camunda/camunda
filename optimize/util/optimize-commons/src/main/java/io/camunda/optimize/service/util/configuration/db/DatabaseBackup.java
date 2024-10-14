/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.db;

import com.fasterxml.jackson.annotation.JsonProperty;

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
    final int PRIME = 59;
    int result = 1;
    final Object $snapshotRepositoryName = getSnapshotRepositoryName();
    result =
        result * PRIME
            + ($snapshotRepositoryName == null ? 43 : $snapshotRepositoryName.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DatabaseBackup)) {
      return false;
    }
    final DatabaseBackup other = (DatabaseBackup) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$snapshotRepositoryName = getSnapshotRepositoryName();
    final Object other$snapshotRepositoryName = other.getSnapshotRepositoryName();
    if (this$snapshotRepositoryName == null
        ? other$snapshotRepositoryName != null
        : !this$snapshotRepositoryName.equals(other$snapshotRepositoryName)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DatabaseBackup(snapshotRepositoryName=" + getSnapshotRepositoryName() + ")";
  }
}
