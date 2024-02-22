/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatusCode;

class BackupAlreadyExistsException extends RuntimeException {

  BackupAlreadyExistsException(final BackupIdentifier id, final BackupStatusCode status) {
    super("Backup with id %s already exists, status of the backup is %s".formatted(id, status));
  }
}
