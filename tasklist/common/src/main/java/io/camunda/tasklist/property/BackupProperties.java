/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.property;

public class BackupProperties {

  private String repositoryName;

  public String getRepositoryName() {
    return repositoryName;
  }

  public BackupProperties setRepositoryName(final String repositoryName) {
    this.repositoryName = repositoryName;
    return this;
  }
}
