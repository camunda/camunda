/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup.repository;

public record BackupRepositoryPropsRecord(
    String version,
    String repositoryName,
    int snapshotTimeout,
    Long incompleteCheckTimeoutInSeconds)
    implements BackupRepositoryProps {

  public BackupRepositoryPropsRecord(final String version, final String repositoryName) {
    this(
        version,
        repositoryName,
        BackupRepositoryProps.EMPTY.snapshotTimeout(),
        BackupRepositoryProps.EMPTY.incompleteCheckTimeoutInSeconds());
  }
}
