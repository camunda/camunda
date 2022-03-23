/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.MaxEntryLimitHitException;
import org.camunda.optimize.service.SearchableIdentityCache;
import org.camunda.optimize.service.es.reader.AssigneeAndCandidateGroupsReader;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

@Component
public class PlatformUserTaskIdentityCache extends AbstractPlatformIdentityCache {
  private final EngineContextFactory engineContextFactory;
  private final AssigneeAndCandidateGroupsReader assigneeAndCandidateGroupsReader;

  public PlatformUserTaskIdentityCache(final ConfigurationService configurationService,
                                       final EngineContextFactory engineContextFactory,
                                       final AssigneeAndCandidateGroupsReader assigneeAndCandidateGroupsReader,
                                       final BackoffCalculator backoffCalculator) {
    super(configurationService::getUserTaskIdentityCacheConfiguration, Collections.emptyList(), backoffCalculator);
    this.engineContextFactory = engineContextFactory;
    this.assigneeAndCandidateGroupsReader = assigneeAndCandidateGroupsReader;
  }

  @Override
  protected String getCacheLabel() {
    return "platform assignee/candidateGroup";
  }

  @Override
  protected void populateCache(final SearchableIdentityCache newIdentityCache) {
    engineContextFactory.getConfiguredEngines()
      .forEach(engineContext -> {
        try {
          assigneeAndCandidateGroupsReader.consumeAssigneesInBatches(
            engineContext.getEngineAlias(),
            assigneeIds -> newIdentityCache.addIdentities(fetchUsersById(engineContext, assigneeIds)),
            getCacheConfiguration().getMaxPageSize()
          );
          assigneeAndCandidateGroupsReader.consumeCandidateGroupsInBatches(
            engineContext.getEngineAlias(),
            groupIds -> newIdentityCache.addIdentities(fetchGroupsById(engineContext, groupIds)),
            getCacheConfiguration().getMaxPageSize()
          );
        } catch (MaxEntryLimitHitException e) {
          throw e;
        } catch (Exception e) {
          log.error("Failed to sync {} identities from engine {}", getCacheLabel(), engineContext.getEngineAlias(), e);
        }
      });
  }

  public List<UserDto> getAssigneesByIds(final Collection<String> assigneeIds) {
    return getUserIdentitiesById(assigneeIds);
  }

  public List<GroupDto> getCandidateGroupsByIds(final Collection<String> candidateGroupIds) {
    return getCandidateGroupIdentitiesById(candidateGroupIds);
  }

  public void resolveAndAddIdentities(final Set<IdentityDto> identities) {
    if (identities.isEmpty()) {
      return;
    }

    final Map<IdentityType, Set<String>> identitiesByType = identities.stream().collect(groupingBy(
      IdentityDto::getType, Collectors.mapping(IdentityDto::getId, Collectors.toSet())
    ));
    final Set<String> userIds = identitiesByType.getOrDefault(IdentityType.USER, Collections.emptySet());
    final Set<String> groupIds = identitiesByType.getOrDefault(IdentityType.GROUP, Collections.emptySet());

    engineContextFactory.getConfiguredEngines()
      .forEach(engineContext -> {
        try {
          getActiveIdentityCache().addIdentities(fetchUsersById(engineContext, userIds));
          getActiveIdentityCache().addIdentities(fetchGroupsById(engineContext, groupIds));
        } catch (MaxEntryLimitHitException e) {
          throw e;
        } catch (Exception e) {
          log.error(
            "Failed to resolve and add {} identities from engine {}", getCacheLabel(), engineContext.getEngineAlias(), e
          );
        }
      });


  }
}
