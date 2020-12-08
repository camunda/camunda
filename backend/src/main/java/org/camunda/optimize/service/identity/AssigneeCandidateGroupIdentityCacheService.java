/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.identity;

import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.MaxEntryLimitHitException;
import org.camunda.optimize.service.SearchableIdentityCache;
import org.camunda.optimize.service.es.reader.AssigneeAndCandidateGroupsReader;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.engine.IdentitySyncConfiguration;
import org.springframework.stereotype.Component;

import java.util.Collections;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.IDENTITY_SYNC_CONFIGURATION;

@Component
public class AssigneeCandidateGroupIdentityCacheService extends AbstractIdentityCacheService {

  private static final String ERROR_INCREASE_CACHE_LIMIT = String.format(
    "Please increase %s.%s in the configuration.",
    // will get a dedicated config with OPT-4633
    IDENTITY_SYNC_CONFIGURATION,
    IdentitySyncConfiguration.Fields.maxEntryLimit.name()
  );

  private final EngineContextFactory engineContextFactory;
  private final AssigneeAndCandidateGroupsReader assigneeAndCandidateGroupsReader;

  public AssigneeCandidateGroupIdentityCacheService(final ConfigurationService configurationService,
                                                    final EngineContextFactory engineContextFactory,
                                                    final AssigneeAndCandidateGroupsReader assigneeAndCandidateGroupsReader,
                                                    final BackoffCalculator backoffCalculator) {
    super(configurationService::getIdentitySyncConfiguration, Collections.emptyList(), backoffCalculator);
    this.engineContextFactory = engineContextFactory;
    this.assigneeAndCandidateGroupsReader = assigneeAndCandidateGroupsReader;
  }

  @Override
  protected String getCacheLabel() {
    return "assignee/candidateGroup";
  }

  @Override
  protected String createIncreaseCacheLimitErrorMessage() {
    return ERROR_INCREASE_CACHE_LIMIT;
  }

  @Override
  protected void populateCache(final SearchableIdentityCache newIdentityCache) {
    engineContextFactory.getConfiguredEngines()
      .forEach(engineContext -> {
        try {
          assigneeAndCandidateGroupsReader.consumeAssigneesInBatches(
            engineContext.getEngineAlias(),
            assigneeIds -> newIdentityCache.addIdentities(engineContext.getUsersById(assigneeIds)),
            getIdentitySyncConfiguration().getMaxPageSize()
          );
          assigneeAndCandidateGroupsReader.consumeCandidateGroupsInBatches(
            engineContext.getEngineAlias(),
            groupIds -> newIdentityCache.addIdentities(engineContext.getGroupsById(groupIds)),
            getIdentitySyncConfiguration().getMaxPageSize()
          );
        } catch (MaxEntryLimitHitException e) {
          throw e;
        } catch (Exception e) {
          log.error("Failed to sync {} identities from engine {}", getCacheLabel(), engineContext.getEngineAlias(), e);
        }
      });
  }
}
