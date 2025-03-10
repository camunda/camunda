/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.config.operate;

public class BackupProperties {

  private String repositoryName;
  private int snapshotTimeout = 0;
  private Long incompleteCheckTimeoutInSeconds = 5 /* minutes */ * 60L;

  public String getRepositoryName() {
    return repositoryName;
  }

  public BackupProperties setRepositoryName(final String repositoryName) {
    this.repositoryName = repositoryName;
    return this;
  }

  public int getSnapshotTimeout() {
    return snapshotTimeout;
  }

  public BackupProperties setSnapshotTimeout(final int snapshotTimeout) {
    this.snapshotTimeout = snapshotTimeout;
    return this;
  }

  public Long getIncompleteCheckTimeoutInSeconds() {
    return incompleteCheckTimeoutInSeconds;
  }

  public void setIncompleteCheckTimeoutInSeconds(final Long incompleteCheckTimeoutInSeconds) {
    this.incompleteCheckTimeoutInSeconds = incompleteCheckTimeoutInSeconds;
  }
}
