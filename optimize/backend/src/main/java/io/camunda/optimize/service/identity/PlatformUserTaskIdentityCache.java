/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Sets;
import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import io.camunda.optimize.rest.engine.EngineContextFactory;
import io.camunda.optimize.service.SearchableIdentityCache;
import io.camunda.optimize.service.db.reader.AssigneeAndCandidateGroupsReader;
import io.camunda.optimize.service.exceptions.MaxEntryLimitHitException;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CamundaPlatformCondition.class)
public class PlatformUserTaskIdentityCache extends AbstractPlatformUserTaskIdentityCache {

  public PlatformUserTaskIdentityCache(
      final ConfigurationService configurationService,
      final EngineContextFactory engineContextFactory,
      final AssigneeAndCandidateGroupsReader assigneeAndCandidateGroupsReader,
      final BackoffCalculator backoffCalculator) {
    super(
        configurationService,
        engineContextFactory,
        assigneeAndCandidateGroupsReader,
        backoffCalculator);
  }

  @Override
  protected String getCacheLabel() {
    return "platform assignee/candidateGroup";
  }

  @Override
  protected void populateCache(final SearchableIdentityCache newIdentityCache) {
    engineContextFactory
        .getConfiguredEngines()
        .forEach(
            engineContext -> {
              try {
                assigneeAndCandidateGroupsReader.consumeAssigneesInBatches(
                    engineContext.getEngineAlias(),
                    assigneeIds ->
                        newIdentityCache.addIdentities(fetchUsersById(engineContext, assigneeIds)),
                    getCacheConfiguration().getMaxPageSize());
                assigneeAndCandidateGroupsReader.consumeCandidateGroupsInBatches(
                    engineContext.getEngineAlias(),
                    groupIds ->
                        newIdentityCache.addIdentities(fetchGroupsById(engineContext, groupIds)),
                    getCacheConfiguration().getMaxPageSize());
              } catch (final MaxEntryLimitHitException e) {
                throw e;
              } catch (final Exception e) {
                log.error(
                    "Failed to sync {} identities from engine {}",
                    getCacheLabel(),
                    engineContext.getEngineAlias(),
                    e);
              }
            });
  }

  public void addIdentitiesIfNotPresent(final Set<IdentityDto> identities) {
    final Set<IdentityDto> identitiesInCache =
        getIdentities(identities).stream()
            .map(IdentityWithMetadataResponseDto::toIdentityDto)
            .collect(toSet());
    final Sets.SetView<IdentityDto> identitiesToSync =
        Sets.difference(identities, identitiesInCache);
    if (!identitiesToSync.isEmpty()) {
      resolveAndAddIdentities(identitiesToSync);
    }
  }

  @Override
  public List<IdentityWithMetadataResponseDto> getIdentities(
      final Collection<IdentityDto> identities) {
    return getActiveIdentityCache().getIdentities(identities);
  }

  @Override
  public Optional<IdentityWithMetadataResponseDto> getIdentityByIdAndType(
      final String id, final IdentityType type) {
    return getActiveIdentityCache().getIdentityByIdAndType(id, type);
  }

  @Override
  public IdentitySearchResultResponseDto searchAmongIdentitiesWithIds(
      final String terms,
      final Collection<String> identityIds,
      final IdentityType identityType,
      final int resultLimit) {
    return searchAmongIdentitiesWithIds(
        terms, identityIds, new IdentityType[] {identityType}, resultLimit);
  }

  public IdentitySearchResultResponseDto searchAmongIdentitiesWithIds(
      final String terms,
      final Collection<String> identityIds,
      final IdentityType[] identityTypes,
      final int resultLimit) {
    return getActiveIdentityCache()
        .searchAmongIdentitiesWithIds(terms, identityIds, identityTypes, resultLimit);
  }

  private void resolveAndAddIdentities(final Set<IdentityDto> identities) {
    if (identities.isEmpty()) {
      return;
    }

    final Map<IdentityType, Set<String>> identitiesByType =
        identities.stream()
            .collect(
                groupingBy(IdentityDto::getType, Collectors.mapping(IdentityDto::getId, toSet())));
    final Set<String> userIds =
        identitiesByType.getOrDefault(IdentityType.USER, Collections.emptySet());
    final Set<String> groupIds =
        identitiesByType.getOrDefault(IdentityType.GROUP, Collections.emptySet());

    engineContextFactory
        .getConfiguredEngines()
        .forEach(
            engineContext -> {
              try {
                getActiveIdentityCache().addIdentities(fetchUsersById(engineContext, userIds));
                getActiveIdentityCache().addIdentities(fetchGroupsById(engineContext, groupIds));
              } catch (final MaxEntryLimitHitException e) {
                throw e;
              } catch (final Exception e) {
                log.error(
                    "Failed to resolve and add {} identities from engine {}",
                    getCacheLabel(),
                    engineContext.getEngineAlias(),
                    e);
              }
            });
  }
}
