/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.identity;

import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UserTaskIdentityCacheServiceSchedulerIT extends AbstractIT {

  @Test
  public void verifySyncEnabledByDefault() {
    assertThat(getIdentityCacheService().isScheduledToRun()).isTrue();
  }

  @Test
  public void testSyncStoppedSuccessfully() {
    try {
      getIdentityCacheService().stopScheduledSync();
      assertThat(getIdentityCacheService().isScheduledToRun()).isFalse();
    } finally {
      getIdentityCacheService().startScheduledSync();
    }
  }

  private UserTaskIdentityCacheService getIdentityCacheService() {
    return embeddedOptimizeExtension.getUserTaskIdentityCacheService();
  }
}
