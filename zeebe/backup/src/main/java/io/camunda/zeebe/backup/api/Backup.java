/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

/** Represents a backup */
public interface Backup {

  /**
   * @return the backup identifier
   */
  BackupIdentifier id();

  /**
   * @return the backup descriptor which contains additional information about the backup
   */
  BackupDescriptor descriptor();

  /**
   * @return the set of snapshot files
   */
  NamedFileSet snapshot();

  /**
   * @return the set of segment files
   */
  NamedFileSet segments();
}
