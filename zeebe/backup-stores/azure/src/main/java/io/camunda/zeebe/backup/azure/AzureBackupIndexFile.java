/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import io.camunda.zeebe.backup.api.BackupIndexFile;
import io.camunda.zeebe.backup.api.BackupIndexIdentifier;
import java.nio.file.Path;

public final class AzureBackupIndexFile implements BackupIndexFile {
  private final BackupIndexIdentifier id;
  private final Path path;
  private String eTag;

  AzureBackupIndexFile(final Path path, final BackupIndexIdentifier id) {
    this.id = id;
    this.path = path;
  }

  public AzureBackupIndexFile(final Path path, final BackupIndexIdentifier id, final String eTag) {
    this.id = id;
    this.path = path;
    this.eTag = eTag;
  }

  @Override
  public BackupIndexIdentifier id() {
    return id;
  }

  @Override
  public Path path() {
    return path;
  }

  String getETag() {
    return eTag;
  }

  void setETag(final String eTag) {
    this.eTag = eTag;
  }
}
