/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import java.nio.file.Path;

/**
 * An opaque holder for a {@link BackupIndex}.
 *
 * @implNote Some implementations might contain additional mutable state that allows the {@link
 *     BackupStore} to detect and prevent against illegal concurrent modifications.
 */
public interface BackupIndexFile {
  BackupIndexIdentifier id();

  Path path();
}
