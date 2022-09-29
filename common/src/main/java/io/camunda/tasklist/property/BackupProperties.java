/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.property;

public class BackupProperties {

  private String repositoryName;

  public String getRepositoryName() {
    return repositoryName;
  }

  public BackupProperties setRepositoryName(String repositoryName) {
    this.repositoryName = repositoryName;
    return this;
  }
}
