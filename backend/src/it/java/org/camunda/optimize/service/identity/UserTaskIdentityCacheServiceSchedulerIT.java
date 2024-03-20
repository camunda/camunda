/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;

import org.camunda.optimize.AbstractPlatformIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class UserTaskIdentityCacheServiceSchedulerIT extends AbstractPlatformIT {

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

  private PlatformUserTaskIdentityCache getIdentityCacheService() {
    return embeddedOptimizeExtension.getUserTaskIdentityCache();
  }
}
