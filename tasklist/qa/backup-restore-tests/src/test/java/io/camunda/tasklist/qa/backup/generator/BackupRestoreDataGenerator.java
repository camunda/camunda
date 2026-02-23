/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.backup.generator;

import io.camunda.tasklist.qa.backup.BackupRestoreTestContext;
import java.io.IOException;
import org.springframework.resilience.annotation.Retryable;

/** Interface for backup/restore test data generators. */
public interface BackupRestoreDataGenerator {

  void createData(BackupRestoreTestContext testContext) throws Exception;

  @Retryable(maxRetries = 10, delay = 2000, includes = AssertionError.class)
  void assertData() throws IOException;

  @Retryable(maxRetries = 10, delay = 2000, includes = AssertionError.class)
  void assertDataAfterChange() throws IOException;

  void changeData(BackupRestoreTestContext testContext) throws IOException;
}
