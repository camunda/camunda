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

  BackupRepositoryProps EMPTY =
      new BackupRepositoryProps() {
        @Override
        public String version() {
          return null;
        }

        @Override
        public String repositoryName() {
          return null;
        }
      };

  String version();

  String repositoryName();

  default int snapshotTimeout() {
    return 0;
  }

  default Long incompleteCheckTimeoutInSeconds() {
    return defaultIncompleteCheckTimeoutInSeconds();
  }

  static Long defaultIncompleteCheckTimeoutInSeconds() {
    return 5 * 60L;
  }

  default boolean includeGlobalState() {
    return true;
  }
}
