/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import static java.util.stream.Collectors.toMap;

import com.google.common.collect.Sets;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.GroupDto;
import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import io.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupDefinitionSearchRequestDto;
import io.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupReportSearchRequestDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.service.db.reader.AssigneeAndCandidateGroupsReader;
import io.camunda.optimize.service.identity.UserTaskIdentityService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import jakarta.ws.rs.ForbiddenException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Slf4j
@Component
public class AssigneeCandidateGroupService {

  public static final IdentityType ASSIGNEE_IDENTITY_TYPE = IdentityType.USER;
  private static final IdentityType CANDIDATE_GROUP_IDENTITY_TYPE = IdentityType.GROUP;

  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final AssigneeAndCandidateGroupsReader assigneeAndCandidateGroupsReader;
  private final UserTaskIdentityService userTaskIdentityService;
  private final ReportService reportService;

  public Optional<IdentityWithMetadataResponseDto> getIdentityByIdAndType(
      final String id, final IdentityType type) {
    return userTaskIdentityService.getIdentityByIdAndType(id, type);
  }

  public List<UserDto> getAssigneesByIds(final Collection<String> assigneeIds) {
    final Map<String, UserDto> assigneesById =
        userTaskIdentityService.getAssigneesByIds(assigneeIds).stream()
            .collect(toMap(IdentityDto::getId, Function.identity()));
    return assigneeIds.stream().map(id -> assigneesById.getOrDefault(id, new UserDto(id))).toList();
  }

  public IdentitySearchResultResponseDto searchForAssigneesAsUser(
      @NonNull final String userId,
      @NonNull final AssigneeCandidateGroupDefinitionSearchRequestDto requestDto) {
    return searchForAssignees(
        requestDto.getLimit(),
        requestDto.getTerms().orElse(""),
        validateAccessAndCreateDefinitionTenantMap(
            userId, requestDto.getProcessDefinitionKey(), requestDto.getTenantIds()));
  }

  public IdentitySearchResultResponseDto searchForAssigneesAsUser(
      @NonNull final String userId,
      @NonNull final AssigneeCandidateGroupReportSearchRequestDto requestDto) {
    return searchForAssignees(
        requestDto.getLimit(),
        requestDto.getTerms().orElse(""),
        retrieveAuthorizedDefinitionTenantMap(userId, requestDto.getReportIds()));
  }

  public List<GroupDto> getCandidateGroupsByIds(final Collection<String> candidateGroupIds) {
    final Map<String, GroupDto> candidateGroupsById =
        userTaskIdentityService.getCandidateGroupIdentitiesById(candidateGroupIds).stream()
            .collect(toMap(IdentityDto::getId, Function.identity()));
    return candidateGroupIds.stream()
        .map(id -> candidateGroupsById.getOrDefault(id, new GroupDto(id)))
        .toList();
  }

  public IdentitySearchResultResponseDto searchForCandidateGroupsAsUser(
      @NonNull final String userId,
      @NonNull final AssigneeCandidateGroupDefinitionSearchRequestDto requestDto) {
    return searchForCandidateGroups(
        requestDto.getLimit(),
        requestDto.getTerms().orElse(""),
        validateAccessAndCreateDefinitionTenantMap(
            userId, requestDto.getProcessDefinitionKey(), requestDto.getTenantIds()));
  }

  public IdentitySearchResultResponseDto searchForCandidateGroupsAsUser(
      @NonNull final String userId,
      @NonNull final AssigneeCandidateGroupReportSearchRequestDto requestDto) {
    return searchForCandidateGroups(
        requestDto.getLimit(),
        requestDto.getTerms().orElse(""),
        retrieveAuthorizedDefinitionTenantMap(userId, requestDto.getReportIds()));
  }

  private IdentitySearchResultResponseDto searchForAssignees(
      final int limit,
      @NonNull final String terms,
      @NonNull final Map<String, Set<String>> definitionKeyToTenantsMap) {
    // this is not efficient but a compromise assuming assignee cardinality is usually within a
    // handleable frame
    // and that the effort to enrich the cache data with the definition scope is for now too complex
    // to be worth it
    final Set<String> assigneeIdsForProcess =
        assigneeAndCandidateGroupsReader.getAssigneeIdsForProcess(definitionKeyToTenantsMap);
    return userTaskIdentityService.searchAmongIdentitiesWithIds(
        terms, assigneeIdsForProcess, ASSIGNEE_IDENTITY_TYPE, limit);
  }

  private IdentitySearchResultResponseDto searchForCandidateGroups(
      final int limit,
      @NonNull final String terms,
      @NonNull final Map<String, Set<String>> definitionKeyToTenantsMap) {
    // this is not efficient but a compromise assuming assignee cardinality is usually within a
    // handleable frame
    // and that the effort to enrich the cache data with the definition scope is for now too complex
    // to be worth it
    final Set<String> candidateGroupIdsForProcess =
        assigneeAndCandidateGroupsReader.getCandidateGroupIdsForProcess(definitionKeyToTenantsMap);
    return userTaskIdentityService.searchAmongIdentitiesWithIds(
        terms, candidateGroupIdsForProcess, CANDIDATE_GROUP_IDENTITY_TYPE, limit);
  }

  private Map<String, Set<String>> retrieveAuthorizedDefinitionTenantMap(
      @NonNull final String userId, @NonNull final List<String> reportIds) {
    final List<ReportDefinitionDto> reports =
        reportService.getAllAuthorizedReportsForIds(userId, reportIds);
    // Add all single reports contained within combined reports
    reports.addAll(
        reports.stream()
            .filter(CombinedReportDefinitionRequestDto.class::isInstance)
            .map(CombinedReportDefinitionRequestDto.class::cast)
            .flatMap(
                report ->
                    reportService
                        .getAllAuthorizedReportsForIds(userId, report.getData().getReportIds())
                        .stream())
            .collect(Collectors.toList()));
    return reports.stream()
        .filter(SingleProcessReportDefinitionRequestDto.class::isInstance)
        .map(SingleProcessReportDefinitionRequestDto.class::cast)
        .map(ReportDefinitionDto::getData)
        .map(SingleReportDataDto::getDefinitions)
        .flatMap(Collection::stream)
        .collect(
            toMap(
                ReportDataDefinitionDto::getKey,
                definition -> new HashSet<>(definition.getTenantIds()),
                (u, v) -> {
                  u.addAll(v);
                  return u;
                }));
  }

  private Map<String, Set<String>> validateAccessAndCreateDefinitionTenantMap(
      @NonNull final String userId,
      @NonNull final String definitionKey,
      @NonNull final List<String> tenantIds) {
    validateDefinitionAccess(userId, definitionKey, tenantIds);
    final Map<String, Set<String>> definitionKeyToTenantsMap = new HashMap<>();
    definitionKeyToTenantsMap.put(definitionKey, Sets.newHashSet(tenantIds));
    return definitionKeyToTenantsMap;
  }

  private void validateDefinitionAccess(
      final @NonNull String userId,
      final String processDefinitionKey,
      final List<String> tenantIds) {
    if (!definitionAuthorizationService.isAuthorizedToAccessDefinition(
        userId, DefinitionType.PROCESS, processDefinitionKey, tenantIds)) {
      throw new ForbiddenException(
          "Current user is not authorized to access data of the provided definition or tenants");
    }
  }
}
