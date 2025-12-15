/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.api.BackupIndexFile;
import io.camunda.zeebe.backup.api.BackupIndexIdentifier;
import java.nio.file.Path;

public record S3BackupIndexFile(BackupIndexIdentifier id, Path path, String eTag)
    implements BackupIndexFile {

  S3BackupIndexFile(final Path path, final BackupIndexIdentifier id) {
    this(id, path, null);
  }

  @Override
  public BackupIndexIdentifier id() {
    return id;
  }

  @Override
  public Path path() {
    return path;
  }
}
