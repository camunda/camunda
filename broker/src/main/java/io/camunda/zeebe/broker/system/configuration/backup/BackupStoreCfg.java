/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;

public class BackupStoreCfg implements ConfigurationEntry {

  private BackupStoreType store = BackupStoreType.NONE;

  private S3BackupStoreConfig s3 = new S3BackupStoreConfig();

  public S3BackupStoreConfig getS3() {
    return s3;
  }

  public void setS3(final S3BackupStoreConfig s3) {
    this.s3 = s3;
  }

  public BackupStoreType getStore() {
    return store;
  }

  public void setStore(final BackupStoreType store) {
    this.store = store;
  }

  @Override
  public String toString() {
    return "BackupStoreCfg{" + "store=" + store + ", s3=" + s3 + '}';
  }

  public enum BackupStoreType {
    /**
     * When type = S3, {@link io.camunda.zeebe.backup.s3.S3BackupStore} will be used as the backup
     * store
     */
    S3,

    /** Set type = NONE when no backup store is available. No backup will be taken. */
    NONE
  }
}
