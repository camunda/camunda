/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.qa.backup.generator;

import io.camunda.tasklist.qa.backup.BackupRestoreTestContext;
import java.io.IOException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

public interface BackupRestoreDataGenerator {

  void createData(BackupRestoreTestContext testContext) throws Exception;

  @Retryable(retryFor = AssertionError.class, maxAttempts = 10, backoff = @Backoff(delay = 2000))
  void assertData() throws IOException;

  @Retryable(retryFor = AssertionError.class, maxAttempts = 10, backoff = @Backoff(delay = 2000))
  void assertDataAfterChange() throws IOException;

  void changeData(BackupRestoreTestContext testContext) throws IOException;
}
