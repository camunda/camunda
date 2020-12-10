/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;


import com.google.common.collect.Sets;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeRequestDto;
import org.camunda.optimize.service.es.reader.AssigneeAndCandidateGroupsReader;
import org.camunda.optimize.service.identity.AssigneeCandidateGroupIdentityCacheService;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.camunda.optimize.service.security.IdentityAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.util.ValidationHelper.ensureNotEmpty;

@RequiredArgsConstructor
@Slf4j
@Component
public class AssigneeCandidateGroupService {

  private static final IdentityType[] ASSIGNEE_IDENTITY_TYPES = {IdentityType.USER};
  private static final IdentityType[] CANDIDATE_GROUP_IDENTITY_TYPES = {IdentityType.GROUP};

  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final AssigneeAndCandidateGroupsReader assigneeAndCandidateGroupsReader;
  private final AssigneeCandidateGroupIdentityCacheService identityCacheService;
  private final IdentityAuthorizationService identityAuthorizationService;

  public List<String> getAllAssigneeIdsForProcess(@NonNull final String userId,
                                                  @NonNull final AssigneeRequestDto requestDto) {
    ensureNotEmpty("process definition key", requestDto.getProcessDefinitionKey());
    validateDefinitionAccess(userId, requestDto);
    return assigneeAndCandidateGroupsReader.getAssignees(requestDto);
  }

  public List<String> getAllCandidateGroups(@NonNull final String userId,
                                            @NonNull final AssigneeRequestDto requestDto) {
    ensureNotEmpty("process definition key", requestDto.getProcessDefinitionKey());
    validateDefinitionAccess(userId, requestDto);
    return assigneeAndCandidateGroupsReader.getCandidateGroups(requestDto);
  }

  public IdentitySearchResultResponseDto searchForAssignees(@NonNull final String userId,
                                                            @NonNull final String searchString,
                                                            final int maxResults) {
    return searchForIdentitiesAsUser(userId, searchString, ASSIGNEE_IDENTITY_TYPES, maxResults);
  }

  public IdentitySearchResultResponseDto searchForCandidateGroups(@NonNull final String userId,
                                                                  @NonNull final String searchString,
                                                                  final int maxResults) {
    return searchForIdentitiesAsUser(userId, searchString, CANDIDATE_GROUP_IDENTITY_TYPES, maxResults);
  }

  public void addIdentitiesIfNotPresent(final Set<IdentityDto> identities) {
    final Set<IdentityDto> identitiesInCache = identityCacheService.getIdentities(identities)
      .stream().map(IdentityWithMetadataResponseDto::toIdentityDto).collect(toSet());
    final Sets.SetView<IdentityDto> identitiesToSync = Sets.difference(identities, identitiesInCache);
    if (!identitiesToSync.isEmpty()) {
      identityCacheService.resolveAndAddIdentities(identitiesToSync);
    }
  }

  private IdentitySearchResultResponseDto searchForIdentitiesAsUser(final String userId,
                                                                    final String searchString,
                                                                    final IdentityType[] identityTypes,
                                                                    final int maxResults) {
    final List<IdentityWithMetadataResponseDto> filteredIdentities = new ArrayList<>();
    IdentitySearchResultResponseDto result =
      identityCacheService.searchIdentities(searchString, identityTypes, maxResults);
    while (!result.getResult().isEmpty()
      && filteredIdentities.size() < maxResults) {
      // continue searching until either the maxResult number of hits has been found or
      // the end of the cache has been reached
      filteredIdentities.addAll(filterIdentitySearchResultByUserAuthorizations(userId, result));
      result = identityCacheService.searchIdentitiesAfter(searchString, identityTypes, maxResults, result);
    }
    return new IdentitySearchResultResponseDto(result.getTotal(), filteredIdentities);
  }

  private List<IdentityWithMetadataResponseDto> filterIdentitySearchResultByUserAuthorizations(
    final String userId,
    final IdentitySearchResultResponseDto result) {
    return result.getResult()
      .stream()
      .filter(identity -> identityAuthorizationService
        .isUserAuthorizedToSeeIdentity(userId, identity.getType(), identity.getId())
      )
      .collect(toList());
  }

  private void validateDefinitionAccess(final @NonNull String userId, final @NonNull AssigneeRequestDto requestDto) {
    if (!definitionAuthorizationService.isAuthorizedToAccessDefinition(
      userId, DefinitionType.PROCESS, requestDto.getProcessDefinitionKey(), requestDto.getTenantIds()
    )) {
      throw new ForbiddenException(
        "Current user is not authorized to access data of the provided definition or tenants"
      );
    }
  }

}
