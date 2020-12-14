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
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeRequestDto;
import org.camunda.optimize.service.es.reader.AssigneeAndCandidateGroupsReader;
import org.camunda.optimize.service.identity.AssigneeCandidateGroupIdentityCacheService;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.util.ValidationHelper.ensureNotEmpty;

@RequiredArgsConstructor
@Slf4j
@Component
public class AssigneeCandidateGroupService {

  private static final IdentityType[] ASSIGNEE_IDENTITY_TYPES = {IdentityType.USER};
  private static final IdentityType[] CANDIDATE_GROUP_IDENTITY_TYPES = {IdentityType.GROUP};
  private static final String PROCESS_DEFINITION_KEY = "process definition key";

  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final AssigneeAndCandidateGroupsReader assigneeAndCandidateGroupsReader;
  private final AssigneeCandidateGroupIdentityCacheService identityCacheService;

  public List<UserDto> getAssigneesByIds(final Collection<String> assigneeIds) {
    final Map<String, UserDto> assigneesById = identityCacheService
      .getAssigneesByIds(assigneeIds)
      .stream()
      .collect(toMap(IdentityDto::getId, Function.identity()));
    return assigneeIds
      .stream()
      .map(id -> assigneesById.getOrDefault(id, new UserDto(id)))
      .collect(Collectors.toList());
  }

  public List<String> getAllAssigneeIdsForProcess(@NonNull final String userId,
                                                  @NonNull final AssigneeRequestDto requestDto) {
    ensureNotEmpty(PROCESS_DEFINITION_KEY, requestDto.getProcessDefinitionKey());
    validateDefinitionAccess(userId, requestDto.getProcessDefinitionKey(), requestDto.getTenantIds());
    return assigneeAndCandidateGroupsReader.getAssignees(requestDto);
  }

  public IdentitySearchResultResponseDto searchForAssigneesAsUser(
    @NonNull final String userId,
    @NonNull final AssigneeCandidateGroupSearchRequestDto requestDto) {
    validateDefinitionAccess(userId, requestDto.getProcessDefinitionKey(), requestDto.getTenantIds());

    // this is not efficient but a compromise assuming assignee cardinality is usually within a handleable frame
    // and that the effort to enrich the cache data with the definition scope is for now too complex to be worth it
    final Set<String> assigneeIdsForProcess = assigneeAndCandidateGroupsReader.getAssigneeIdsForProcess(
      requestDto.getProcessDefinitionKey(), requestDto.getTenantIds()
    );
    return identityCacheService.searchAmongIdentitiesWithIds(
      requestDto.getTerms().orElse(""), assigneeIdsForProcess, ASSIGNEE_IDENTITY_TYPES, requestDto.getLimit()
    );
  }

  public List<GroupDto> getCandidateGroupsByIds(final Collection<String> assigneeIds) {
    final Map<String, GroupDto> candidateGroupsById = identityCacheService
      .getCandidateGroupIdentitiesById(assigneeIds)
      .stream()
      .collect(toMap(IdentityDto::getId, Function.identity()));
    return assigneeIds
      .stream()
      .map(id -> candidateGroupsById.getOrDefault(id, new GroupDto(id)))
      .collect(Collectors.toList());
  }

  public List<String> getAllCandidateGroups(@NonNull final String userId,
                                            @NonNull final AssigneeRequestDto requestDto) {
    ensureNotEmpty(PROCESS_DEFINITION_KEY, requestDto.getProcessDefinitionKey());
    validateDefinitionAccess(userId, requestDto.getProcessDefinitionKey(), requestDto.getTenantIds());
    return assigneeAndCandidateGroupsReader.getCandidateGroups(requestDto);
  }

  public IdentitySearchResultResponseDto searchForCandidateGroupsAsUser(
    @NonNull final String userId,
    @NonNull final AssigneeCandidateGroupSearchRequestDto requestDto) {
    validateDefinitionAccess(userId, requestDto.getProcessDefinitionKey(), requestDto.getTenantIds());

    // this is not efficient but a compromise assuming assignee cardinality is usually within a handleable frame
    // and that the effort to enrich the cache data with the definition scope is for now too complex to be worth it
    final Set<String> candidateGroupIdsForProcess = assigneeAndCandidateGroupsReader.getCandidateGroupIdsForProcess(
      requestDto.getProcessDefinitionKey(), requestDto.getTenantIds()
    );
    return identityCacheService.searchAmongIdentitiesWithIds(
      requestDto.getTerms().orElse(""),
      candidateGroupIdsForProcess,
      CANDIDATE_GROUP_IDENTITY_TYPES,
      requestDto.getLimit()
    );
  }

  public void addIdentitiesIfNotPresent(final Set<IdentityDto> identities) {
    final Set<IdentityDto> identitiesInCache = identityCacheService.getIdentities(identities)
      .stream().map(IdentityWithMetadataResponseDto::toIdentityDto).collect(toSet());
    final Sets.SetView<IdentityDto> identitiesToSync = Sets.difference(identities, identitiesInCache);
    if (!identitiesToSync.isEmpty()) {
      identityCacheService.resolveAndAddIdentities(identitiesToSync);
    }
  }

  private void validateDefinitionAccess(final @NonNull String userId,
                                        final String processDefinitionKey,
                                        final List<String> tenantIds) {
    if (!definitionAuthorizationService.isAuthorizedToAccessDefinition(
      userId, DefinitionType.PROCESS, processDefinitionKey, tenantIds
    )) {
      throw new ForbiddenException(
        "Current user is not authorized to access data of the provided definition or tenants"
      );
    }
  }

}
