/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import io.camunda.zeebe.backup.api.BackupIndexFile;
import io.camunda.zeebe.backup.api.BackupIndexIdentifier;
import java.nio.file.Path;

public class GcsBackupIndexFile implements BackupIndexFile {
  private final BackupIndexIdentifier id;
  private final Path path;
  private Long generation;

  public GcsBackupIndexFile(final Path path, final BackupIndexIdentifier id) {
    this.path = path;
    this.id = id;
  }

  @Override
  public BackupIndexIdentifier id() {
    return id;
  }

  @Override
  public Path path() {
    return path;
  }

  public Long getGeneration() {
    return generation;
  }

  public void setGeneration(final long generation) {
    this.generation = generation;
  }
}
