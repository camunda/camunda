/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup.repository;

/**
 * Defined as interface so it can be either implemented by a record (with a copy) or by calling
 * directly getters for mutable data (such as mocks)
 */
public interface BackupRepositoryProps {

  String version();

  String repositoryName();

  int snapshotTimeout();

  Long incompleteCheckTimeoutInSeconds();

  static Integer defaultSnapshotTimeout() {
    return 0;
  }

  static Long defaultIncompleteCheckTimeoutInSeconds() {
    return 5 * 60L;
  }
}
