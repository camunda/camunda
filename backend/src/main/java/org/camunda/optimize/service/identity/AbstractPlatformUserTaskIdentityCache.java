/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import java.util.Collections;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.db.reader.AssigneeAndCandidateGroupsReader;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

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
