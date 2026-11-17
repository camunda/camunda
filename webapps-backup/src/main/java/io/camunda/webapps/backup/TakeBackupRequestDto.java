/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup;

public class TakeBackupRequestDto {

  private Long backupId;
  private boolean skipSchemaCheck = false;

  public Long getBackupId() {
    return backupId;
  }

  public boolean isSkipSchemaCheck() {
    return skipSchemaCheck;
  }

  public TakeBackupRequestDto setBackupId(final Long backupId) {
    this.backupId = backupId;
    return this;
  }

  public TakeBackupRequestDto setSkipSchemaCheck(final boolean skipSchemaCheck) {
    this.skipSchemaCheck = skipSchemaCheck;
    return this;
  }
}
