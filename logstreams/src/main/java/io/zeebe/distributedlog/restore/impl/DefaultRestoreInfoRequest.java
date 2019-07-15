/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.impl;

import io.zeebe.distributedlog.restore.RestoreInfoRequest;

public class DefaultRestoreInfoRequest implements RestoreInfoRequest {
  private long localPosition;
  private long backupPosition;

  public DefaultRestoreInfoRequest() {}

  public DefaultRestoreInfoRequest(long localPosition, long backupPosition) {
    this.localPosition = localPosition;
    this.backupPosition = backupPosition;
  }

  @Override
  public long getLatestLocalPosition() {
    return localPosition;
  }

  public void setLatestLocalPosition(long localPosition) {
    this.localPosition = localPosition;
  }

  @Override
  public long getBackupPosition() {
    return backupPosition;
  }

  public void setBackupPosition(long backupPosition) {
    this.backupPosition = backupPosition;
  }

  @Override
  public String toString() {
    return "DefaultRestoreInfoRequest{"
        + "localPosition="
        + localPosition
        + ", backupPosition="
        + backupPosition
        + '}';
  }
}
