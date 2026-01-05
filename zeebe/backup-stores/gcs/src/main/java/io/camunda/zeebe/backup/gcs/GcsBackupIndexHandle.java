/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import io.camunda.zeebe.backup.api.BackupIndexHandle;
import io.camunda.zeebe.backup.api.BackupIndexIdentifier;
import java.nio.file.Path;

public record GcsBackupIndexHandle(BackupIndexIdentifier id, Path path, Long generation)
    implements BackupIndexHandle {

  public GcsBackupIndexHandle(final Path path, final BackupIndexIdentifier id) {
    this(id, path, null);
  }
}
