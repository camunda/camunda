/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.identity;

import io.camunda.optimize.rest.engine.EngineContextFactory;
import io.camunda.optimize.service.db.reader.AssigneeAndCandidateGroupsReader;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;

public abstract class AbstractPlatformUserTaskIdentityCache extends AbstractPlatformIdentityCache
    implements UserTaskIdentityService {

  protected final EngineContextFactory engineContextFactory;
  protected final AssigneeAndCandidateGroupsReader assigneeAndCandidateGroupsReader;

  protected AbstractPlatformUserTaskIdentityCache(
      final ConfigurationService configurationService,
      final EngineContextFactory engineContextFactory,
      final AssigneeAndCandidateGroupsReader assigneeAndCandidateGroupsReader,
      final BackoffCalculator backoffCalculator) {
    super(
        configurationService::getUserTaskIdentityCacheConfiguration,
        Collections.emptyList(),
        backoffCalculator);
    this.engineContextFactory = engineContextFactory;
    this.assigneeAndCandidateGroupsReader = assigneeAndCandidateGroupsReader;
  }
}
