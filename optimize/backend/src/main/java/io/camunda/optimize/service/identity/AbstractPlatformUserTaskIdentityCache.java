/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
