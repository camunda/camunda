/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardAssigneeFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardCandidateGroupFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardInstanceStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardStateFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardVariableFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardIdentityFilterDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.dto.optimize.rest.AuthorizationType;
import io.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import io.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import io.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.exceptions.InvalidDashboardVariableFilterException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.relations.CollectionReferencingService;
import io.camunda.optimize.service.relations.DashboardRelationService;
import io.camunda.optimize.service.relations.ReportReferencingService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.variable.ProcessVariableService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class DashboardService implements ReportReferencingService, CollectionReferencingService {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(DashboardService.class);
  private final DashboardWriter dashboardWriter;
  private final DashboardReader dashboardReader;
  private final ProcessVariableService processVariableService;
  private final ReportService reportService;
  private final AuthorizedCollectionService collectionService;
  private final AbstractIdentityService identityService;
  private final ReportReader reportReader;
  private final DashboardRelationService dashboardRelationService;

  public DashboardService(
      final DashboardWriter dashboardWriter,
      final DashboardReader dashboardReader,
      final ProcessVariableService processVariableService,
      final ReportService reportService,
      final AuthorizedCollectionService collectionService,
      final AbstractIdentityService identityService,
      final ReportReader reportReader,
      final DashboardRelationService dashboardRelationService) {
    this.dashboardWriter = dashboardWriter;
    this.dashboardReader = dashboardReader;
    this.processVariableService = processVariableService;
    this.reportService = reportService;
    this.collectionService = collectionService;
    this.identityService = identityService;
    this.reportReader = reportReader;
    this.dashboardRelationService = dashboardRelationService;
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportDelete(
      final ReportDefinitionDto reportDefinition) {
    return getDashboardsForReport(reportDefinition.getId()).stream()
        .map(
            dashboardDefinitionDto ->
                new ConflictedItemDto(
                    dashboardDefinitionDto.getId(),
                    ConflictedItemType.DASHBOARD,
                    dashboardDefinitionDto.getName()))
        .collect(Collectors.toSet());
  }

  @Override
  public void handleReportDeleted(final ReportDefinitionDto reportDefinition) {
    if (reportDefinition
        instanceof final SingleProcessReportDefinitionRequestDto typeCheckedReportDefinition) {
      final List<ProcessVariableNameResponseDto> varNamesForReportToRemove =
          processVariableService.getVariableNamesForReportDefinitions(
              Collections.singletonList(typeCheckedReportDefinition));
      removeVariableFiltersFromDashboardsIfUnavailable(
          varNamesForReportToRemove, typeCheckedReportDefinition.getId());
    }
    removeReportFromDashboards(reportDefinition.getId());
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportUpdate(
      final ReportDefinitionDto currentDefinition, final ReportDefinitionDto updateDefinition) {
    // NOOP
    return Collections.emptySet();
  }

  @Override
  public void handleReportUpdated(
      final String reportId, final ReportDefinitionDto updateDefinition) {
    final ReportDefinitionDto existingReport =
        reportReader
            .getReport(reportId)
            .orElseThrow(
                () -> new NotFoundException("Report with id [" + reportId + "] does not exist"));
    if (existingReport
        instanceof final SingleProcessReportDefinitionRequestDto existingReportDefinition) {
      final SingleProcessReportDefinitionRequestDto updateReportDefinition =
          (SingleProcessReportDefinitionRequestDto) updateDefinition;
      final List<ProcessVariableNameResponseDto> availableVariableNamesForExistingReport =
          processVariableService.getVariableNamesForReportDefinitions(
              Collections.singletonList(existingReportDefinition));
      final List<ProcessVariableNameResponseDto> availableVariableNamesForUpdatedReport =
          processVariableService.getVariableNamesForReportDefinitions(
              Collections.singletonList(updateReportDefinition));
      availableVariableNamesForExistingReport.removeAll(availableVariableNamesForUpdatedReport);
      removeVariableFiltersFromDashboardsIfUnavailable(
          availableVariableNamesForExistingReport, reportId);
    }
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForCollectionDelete(
      final CollectionDefinitionDto definition) {
    return dashboardReader.getDashboardsForCollection(definition.getId()).stream()
        .map(
            dashboardDefinitionDto ->
                new ConflictedItemDto(
                    dashboardDefinitionDto.getId(),
                    ConflictedItemType.COLLECTION,
                    dashboardDefinitionDto.getName()))
        .collect(Collectors.toSet());
  }

  @Override
  public void handleCollectionDeleted(final CollectionDefinitionDto definition) {
    dashboardWriter.deleteDashboardsOfCollection(definition.getId());
  }

  public IdResponseDto createNewDashboardAndReturnId(
      final String userId, final DashboardDefinitionRestDto dashboardDefinitionDto) {
    collectionService.verifyUserAuthorizedToEditCollectionResources(
        userId, dashboardDefinitionDto.getCollectionId());
    validateEntityEditorAuthorization(dashboardDefinitionDto.getCollectionId());
    validateDashboardFilters(userId, dashboardDefinitionDto);
    return dashboardWriter.createNewDashboard(userId, dashboardDefinitionDto);
  }

  public IdResponseDto copyDashboard(
      final String dashboardId, final String userId, final String name) {
    final AuthorizedDashboardDefinitionResponseDto authorizedDashboard =
        getDashboardDefinition(dashboardId, userId);
    final DashboardDefinitionRestDto dashboardDefinition = authorizedDashboard.getDefinitionDto();

    final String newDashboardName = name != null ? name : dashboardDefinition.getName() + " – Copy";
    return copyAndMoveDashboard(
        dashboardId, userId, dashboardDefinition.getCollectionId(), newDashboardName);
  }

  public IdResponseDto copyAndMoveDashboard(
      final String dashboardId, final String userId, final String collectionId, final String name) {
    return copyAndMoveDashboard(dashboardId, userId, collectionId, name, new HashMap<>(), false);
  }

  public IdResponseDto copyAndMoveDashboard(
      final String dashboardId,
      final String userId,
      final String collectionId,
      final String name,
      final Map<String, String> uniqueReportCopies,
      final boolean keepReportNames) {
    final AuthorizedDashboardDefinitionResponseDto authorizedDashboard =
        getDashboardDefinition(dashboardId, userId);
    final DashboardDefinitionRestDto dashboardDefinition = authorizedDashboard.getDefinitionDto();
    if (dashboardDefinition.isManagementDashboard()
        || dashboardDefinition.isInstantPreviewDashboard()) {
      throw new OptimizeValidationException(
          "Management and Instant preview dashboards cannot be copied");
    }

    collectionService.verifyUserAuthorizedToEditCollectionResources(userId, collectionId);
    validateEntityEditorAuthorization(dashboardDefinition.getCollectionId());

    final List<DashboardReportTileDto> newDashboardReports =
        new ArrayList<>(dashboardDefinition.getTiles());
    if (!isSameCollection(collectionId, dashboardDefinition.getCollectionId())) {
      newDashboardReports.clear();
      containingReportsComplyWithNewCollectionScope(userId, collectionId, dashboardDefinition);
      dashboardDefinition
          .getTiles()
          .forEach(
              reportLocationDto -> {
                final String originalReportId = reportLocationDto.getId();
                if (IdGenerator.isValidId(originalReportId)) {
                  String reportCopyId = uniqueReportCopies.get(originalReportId);
                  if (reportCopyId == null) {
                    final ReportDefinitionDto report =
                        reportReader
                            .getReport(originalReportId)
                            .orElseThrow(
                                () ->
                                    new NotFoundException(
                                        "Was not able to retrieve report with id ["
                                            + originalReportId
                                            + "]"
                                            + "from Database. Report does not exist."));
                    final String newReportName = keepReportNames ? report.getName() : null;
                    reportCopyId =
                        reportService
                            .copyAndMoveReport(
                                originalReportId,
                                userId,
                                collectionId,
                                newReportName,
                                uniqueReportCopies,
                                keepReportNames)
                            .getId();
                    uniqueReportCopies.put(originalReportId, reportCopyId);
                  }
                  newDashboardReports.add(
                      reportLocationDto.toBuilder()
                          .id(reportCopyId)
                          .configuration(reportLocationDto.getConfiguration())
                          .build());
                } else {
                  newDashboardReports.add(reportLocationDto);
                }
              });
    }

    final String newDashboardName = name != null ? name : dashboardDefinition.getName() + " – Copy";
    final DashboardDefinitionRestDto newDashboardDefinitionDto = new DashboardDefinitionRestDto();
    newDashboardDefinitionDto.setCollectionId(collectionId);
    newDashboardDefinitionDto.setName(newDashboardName);
    newDashboardDefinitionDto.setDescription(dashboardDefinition.getDescription());
    newDashboardDefinitionDto.setTiles(newDashboardReports);
    newDashboardDefinitionDto.setAvailableFilters(dashboardDefinition.getAvailableFilters());
    return dashboardWriter.createNewDashboard(userId, newDashboardDefinitionDto);
  }

  public void validateDashboardDescription(final String dashboardDescription) {
    if (dashboardDescription != null) {
      if (dashboardDescription.length() > 400) {
        throw new OptimizeValidationException(
            "Dashboard descriptions cannot be greater than 400 characters");
      } else if (dashboardDescription.isEmpty()) {
        throw new OptimizeValidationException(
            "Dashboard descriptions cannot be non-null and empty");
      }
    }
  }

  private void removeVariableFiltersFromDashboardsIfUnavailable(
      final List<ProcessVariableNameResponseDto> filters, final String reportId) {
    final List<DashboardDefinitionRestDto> dashboardsForReport =
        dashboardReader.getDashboardsForReport(reportId);
    dashboardsForReport.stream()
        .filter(
            dashboard ->
                !extractDashboardVariableFilters(dashboard.getAvailableFilters()).isEmpty())
        .forEach(
            dashboard -> {
              final List<String> otherReportIdsInDashboard =
                  dashboard.getTiles().stream()
                      .filter(tile -> tile.getType() == DashboardTileType.OPTIMIZE_REPORT)
                      .map(DashboardReportTileDto::getId)
                      .filter(reportInDashboardId -> !reportId.equals(reportInDashboardId))
                      .toList();
              final List<SingleProcessReportDefinitionRequestDto> allReportsForIdsOmitXml =
                  reportReader.getAllReportsForIdsOmitXml(otherReportIdsInDashboard).stream()
                      .filter(SingleProcessReportDefinitionRequestDto.class::isInstance)
                      .map(SingleProcessReportDefinitionRequestDto.class::cast)
                      .toList();
              final List<ProcessVariableNameResponseDto> varNamesForReportsToRemain =
                  processVariableService
                      .getVariableNamesForReportDefinitions(allReportsForIdsOmitXml)
                      .stream()
                      // since the filter is being applied across variables with same name and type
                      // in different definitions
                      // independently of the label, we can exclude the label from here.
                      .peek(variableName -> variableName.setLabel(null))
                      .toList();
              final List<DashboardVariableFilterDto> filtersToRemove =
                  extractDashboardVariableFilters(dashboard.getAvailableFilters()).stream()
                      .filter(
                          variableFilter -> {
                            final DashboardVariableFilterDataDto filterData =
                                variableFilter.getData();
                            final ProcessVariableNameResponseDto processVariableForFilter =
                                new ProcessVariableNameResponseDto(
                                    filterData.getName(), filterData.getType(), null);
                            return !varNamesForReportsToRemain.contains(processVariableForFilter)
                                && filters.contains(processVariableForFilter);
                          })
                      .toList();
              if (!filtersToRemove.isEmpty()) {
                dashboard.getAvailableFilters().removeAll(filtersToRemove);
                dashboardWriter.updateDashboard(convertToUpdateDto(dashboard), dashboard.getId());
              }
            });
  }

  private void containingReportsComplyWithNewCollectionScope(
      final String userId,
      final String collectionId,
      final DashboardDefinitionRestDto dashboardDefinition) {
    dashboardDefinition.getTiles().stream()
        .map(DashboardReportTileDto::getId)
        .filter(IdGenerator::isValidId)
        .forEach(
            reportId ->
                reportService.ensureCompliesWithCollectionScope(userId, collectionId, reportId));
  }

  public AuthorizedDashboardDefinitionResponseDto getDashboardDefinition(
      final String dashboardId, final String userId) {
    final DashboardDefinitionRestDto dashboard = getDashboardDefinitionAsService(dashboardId);
    final RoleType currentUserRole = getUserRoleType(userId, dashboard);
    return new AuthorizedDashboardDefinitionResponseDto(currentUserRole, dashboard);
  }

  public AuthorizedDashboardDefinitionResponseDto getManagementDashboard() {
    final DashboardDefinitionRestDto dashboard =
        getDashboardDefinitionAsService(ManagementDashboardService.MANAGEMENT_DASHBOARD_ID);
    return new AuthorizedDashboardDefinitionResponseDto(RoleType.VIEWER, dashboard);
  }

  public void verifyUserHasAccessToDashboardCollection(
      final String userId, final DashboardDefinitionRestDto dashboard) {
    getUserRoleType(userId, dashboard);
  }

  private RoleType getUserRoleType(
      final String userId, final DashboardDefinitionRestDto dashboard) {
    RoleType currentUserRole = null;
    if (dashboard.isManagementDashboard() || dashboard.isInstantPreviewDashboard()) {
      currentUserRole = RoleType.VIEWER;
    } else if (dashboard.getCollectionId() != null) {
      currentUserRole =
          collectionService
              .getUsersCollectionResourceRole(userId, dashboard.getCollectionId())
              .orElse(null);
    } else if (dashboard.getOwner().equals(userId)) {
      currentUserRole = RoleType.EDITOR;
    }

    if (currentUserRole == null) {
      throw new ForbiddenException(
          String.format(
              "User [%s] is not authorized to access dashboard [%s].", userId, dashboard.getId()));
    }
    return currentUserRole;
  }

  public List<IdResponseDto> getAllDashboardIdsInCollection(final String collectionId) {
    return getDashboardDefinitionsInCollectionAsService(collectionId).stream()
        .map(dashboard -> new IdResponseDto(dashboard.getId()))
        .toList();
  }

  public DashboardDefinitionRestDto getDashboardDefinitionAsService(final String dashboardId) {
    final Optional<DashboardDefinitionRestDto> dashboard =
        dashboardReader.getDashboard(dashboardId);

    if (dashboard.isEmpty()) {
      LOG.error("Was not able to retrieve dashboard with id [{}] from Elasticsearch.", dashboardId);
      throw new NotFoundException(
          "Dashboard does not exist! Tried to retrieve dashboard with id " + dashboardId);
    }

    return dashboard.get();
  }

  public List<DashboardDefinitionRestDto> getDashboardDefinitionsAsService(
      final Set<String> dashboardIds) {
    return dashboardReader.getDashboards(dashboardIds);
  }

  public void updateDashboard(
      final DashboardDefinitionRestDto updatedDashboard, final String userId) {
    final String dashboardId = updatedDashboard.getId();
    final AuthorizedDashboardDefinitionResponseDto dashboardWithEditAuthorization =
        getDashboardWithEditAuthorization(dashboardId, userId);
    if (dashboardWithEditAuthorization.getDefinitionDto() != null) {
      if (dashboardWithEditAuthorization.getDefinitionDto().isManagementDashboard()
          || dashboardWithEditAuthorization.getDefinitionDto().isInstantPreviewDashboard()) {
        throw new OptimizeValidationException(
            "Management and Instant preview dashboards cannot be edited");
      } else {
        validateEntityEditorAuthorization(
            dashboardWithEditAuthorization.getDefinitionDto().getCollectionId());
      }
    }

    final DashboardDefinitionUpdateDto updateDto =
        convertToUpdateDtoWithModifier(updatedDashboard, userId);
    final String dashboardCollectionId =
        dashboardWithEditAuthorization.getDefinitionDto().getCollectionId();
    validateDashboardFilters(userId, updatedDashboard);
    updateDto
        .getTiles()
        .forEach(
            tileDto -> {
              if (IdGenerator.isValidId(tileDto.getId())) {
                final ReportDefinitionDto reportDefinition =
                    reportService.getReportDefinition(tileDto.getId(), userId).getDefinitionDto();
                if (!Objects.equals(dashboardCollectionId, reportDefinition.getCollectionId())) {
                  throw new BadRequestException(
                      String.format(
                          "Report %s does not reside in the same collection as the dashboard %s or are both not private entities",
                          reportDefinition.getId(), dashboardId));
                }
              }
            });

    dashboardRelationService.handleUpdated(updatedDashboard);
    dashboardWriter.updateDashboard(updateDto, dashboardId);
  }

  public void deleteDashboard(final String dashboardId) {
    final DashboardDefinitionRestDto dashboardDefinitionDto =
        getDashboardDefinitionAsService(dashboardId);
    deleteDashboard(dashboardId, dashboardDefinitionDto);
  }

  public void deleteDashboardAsUser(final String dashboardId, final String userId) {
    final DashboardDefinitionRestDto dashboardDefinitionDto =
        getDashboardWithEditAuthorization(dashboardId, userId).getDefinitionDto();
    validateEntityCanBeDeletedByUser(dashboardDefinitionDto);
    validateEntityEditorAuthorization(dashboardDefinitionDto.getCollectionId());
    deleteDashboard(dashboardId, dashboardDefinitionDto);
  }

  public void validateDashboardFilters(
      final String userId,
      final List<DashboardFilterDto<?>> availableFilters,
      final List<DashboardReportTileDto> reportsInDashboard) {
    if (!CollectionUtils.isEmpty(availableFilters)) {
      final Map<String, List<DashboardFilterDto<?>>> filtersByClass =
          availableFilters.stream()
              .collect(groupingBy(filter -> filter.getClass().getSimpleName()));
      validateFiltersHaveData(availableFilters);
      validateDateAndStateFilters(filtersByClass);
      validateIdentityFilters(filtersByClass);
      validateVariableFilters(filtersByClass);
      validateVariableFiltersExistInReports(userId, reportsInDashboard, filtersByClass);
    }
  }

  private List<DashboardDefinitionRestDto> getDashboardDefinitionsInCollectionAsService(
      final String collectionId) {
    return dashboardReader.getDashboardsForCollection(collectionId);
  }

  private void deleteDashboard(
      final String dashboardId, final DashboardDefinitionRestDto dashboardDefinitionDto) {
    dashboardRelationService.handleDeleted(dashboardDefinitionDto);
    dashboardWriter.deleteDashboard(dashboardId);
  }

  private AuthorizedDashboardDefinitionResponseDto getDashboardWithEditAuthorization(
      final String dashboardId, final String userId) {
    final AuthorizedDashboardDefinitionResponseDto authorizedDashboardDefinition =
        getDashboardDefinition(dashboardId, userId);
    if (authorizedDashboardDefinition.getCurrentUserRole().ordinal() < RoleType.EDITOR.ordinal()) {
      throw new ForbiddenException(
          String.format(
              "User [%s] is not authorized to edit dashboard [%s].", userId, dashboardId));
    }
    return authorizedDashboardDefinition;
  }

  private void validateDashboardFilters(
      final String userId, final DashboardDefinitionRestDto dashboardDefinitionDto) {
    validateDashboardFilters(
        userId, dashboardDefinitionDto.getAvailableFilters(), dashboardDefinitionDto.getTiles());
  }

  private void validateFiltersHaveData(final List<DashboardFilterDto<?>> availableFilters) {
    final List<DashboardFilterDto<?>> filtersWithoutData =
        availableFilters.stream().filter(filter -> filter.getData() == null).toList();
    if (!filtersWithoutData.isEmpty()) {
      throw new BadRequestException(
          String.format(
              "All filters need to supply Filter data, but Filters %s supplied no data field.",
              filtersWithoutData));
    }
  }

  private void validateVariableFiltersExistInReports(
      final String userId,
      final List<DashboardReportTileDto> reportsInDashboard,
      final Map<String, List<DashboardFilterDto<?>>> filtersByClass) {
    final List<DashboardFilterDto<?>> variableFilters =
        filtersByClass.get(DashboardVariableFilterDto.class.getSimpleName());
    if (!CollectionUtils.isEmpty(variableFilters)) {
      final List<String> reportIdsInDashboard =
          reportsInDashboard.stream()
              .map(DashboardReportTileDto::getId)
              .filter(IdGenerator::isValidId)
              .toList();
      final Map<String, List<VariableType>> possibleVarTypesByName =
          processVariableService
              .getVariableNamesForAuthorizedReports(userId, reportIdsInDashboard)
              .stream()
              .collect(
                  groupingBy(
                      ProcessVariableNameResponseDto::getName,
                      Collectors.mapping(ProcessVariableNameResponseDto::getType, toList())));
      final List<DashboardFilterDto<?>> invalidFilters =
          variableFilters.stream().filter(isInvalidVariableFilter(possibleVarTypesByName)).toList();
      if (!invalidFilters.isEmpty()) {
        throw new InvalidDashboardVariableFilterException(
            String.format(
                "The following variable filter names/types do not exist in any report in dashboard: [%s]",
                invalidFilters));
      }
    }
  }

  private Predicate<DashboardFilterDto<?>> isInvalidVariableFilter(
      final Map<String, List<VariableType>> possibleVarTypesByName) {
    return filter -> {
      final DashboardVariableFilterDataDto filterData =
          ((DashboardVariableFilterDto) filter).getData();
      final List<VariableType> typesByName = possibleVarTypesByName.get(filterData.getName());
      return typesByName == null || !typesByName.contains(filterData.getType());
    };
  }

  private void validateVariableFilters(
      final Map<String, List<DashboardFilterDto<?>>> filtersByClass) {
    final List<DashboardFilterDto<?>> variableFilters =
        filtersByClass.get(DashboardVariableFilterDto.class.getSimpleName());
    if (variableFilters != null) {
      variableFilters.forEach(
          variableFilter -> {
            final DashboardVariableFilterDataDto filterData =
                (DashboardVariableFilterDataDto) variableFilter.getData();
            if (filterData == null) {
              throw new BadRequestException("Variable dashboard filters require additional data");
            }
            final VariableType variableType = filterData.getType();
            if ((variableType.equals(VariableType.DATE)
                    || variableType.equals(VariableType.BOOLEAN))
                && filterData.getData() != null) {
              throw new BadRequestException(
                  String.format(
                      "Filter subdata cannot be supplied for %s variable filters", variableType));
            }
          });
    }
  }

  private void validateDateAndStateFilters(
      final Map<String, List<DashboardFilterDto<?>>> filtersByClass) {
    filtersByClass.entrySet().stream()
        .filter(
            byClass ->
                DashboardInstanceStartDateFilterDto.class.getSimpleName().equals(byClass.getKey())
                    || DashboardInstanceEndDateFilterDto.class
                        .getSimpleName()
                        .equals(byClass.getKey())
                    || DashboardStateFilterDto.class.getSimpleName().equals(byClass.getKey()))
        .forEach(
            byClass -> {
              if (byClass.getValue().size() > 1) {
                throw new BadRequestException(
                    String.format(
                        "There can only be one %s. %s supplied: %s",
                        byClass.getKey(), byClass.getKey(), byClass.getValue()));
              }
            });
  }

  private void validateIdentityFilters(
      final Map<String, List<DashboardFilterDto<?>>> filtersByClass) {
    filtersByClass.entrySet().stream()
        .filter(
            byClass ->
                DashboardAssigneeFilterDto.class.getSimpleName().equals(byClass.getKey())
                    || DashboardCandidateGroupFilterDto.class
                        .getSimpleName()
                        .equals(byClass.getKey()))
        .forEach(
            byClass -> {
              final boolean hasNullData =
                  byClass.getValue().stream().anyMatch(filter -> filter.getData() == null);
              if (hasNullData) {
                throw new BadRequestException("Identity Dashboard filters require additional data");
              }

              final long inCount =
                  byClass.getValue().stream()
                      .filter(
                          filter ->
                              MembershipFilterOperator.IN.equals(
                                  ((DashboardIdentityFilterDataDto) filter.getData())
                                      .getOperator()))
                      .count();
              if (inCount > 1) {
                throw new BadRequestException(
                    String.format(
                        "Duplicate identity filter: %s including %s present. There can only be one including %s.",
                        inCount, byClass.getKey(), byClass.getKey()));
              }

              final long notInCount =
                  byClass.getValue().stream()
                      .filter(
                          filter ->
                              MembershipFilterOperator.NOT_IN.equals(
                                  ((DashboardIdentityFilterDataDto) filter.getData())
                                      .getOperator()))
                      .count();
              if (notInCount > 1) {
                throw new BadRequestException(
                    String.format(
                        "Duplicate identity filter: %s excluding %s present. There can only be one excluding %s.",
                        notInCount, byClass.getKey(), byClass.getKey()));
              }
            });
  }

  private void validateEntityEditorAuthorization(final String collectionId) {
    if (collectionId == null
        && !identityService.getEnabledAuthorizations().contains(AuthorizationType.ENTITY_EDITOR)) {
      throw new ForbiddenException("User is not an authorized entity editor");
    }
  }

  private void validateEntityCanBeDeletedByUser(
      final DashboardDefinitionRestDto dashboardDefinitionDto) {
    if (dashboardDefinitionDto.isManagementDashboard()
        || dashboardDefinitionDto.isInstantPreviewDashboard()) {
      throw new OptimizeValidationException(
          "Management Dashboards and Instant preview dashboards cannot be deleted");
    }
  }

  private void removeReportFromDashboards(final String reportId) {
    dashboardWriter.removeReportFromDashboards(reportId);
  }

  private List<DashboardDefinitionRestDto> getDashboardsForReport(final String reportId) {
    return dashboardReader.getDashboardsForReport(reportId);
  }

  private boolean isSameCollection(final String newCollectionId, final String oldCollectionId) {
    return StringUtils.equals(newCollectionId, oldCollectionId);
  }

  private DashboardDefinitionUpdateDto convertToUpdateDtoWithModifier(
      final DashboardDefinitionRestDto updatedDashboard, final String userId) {
    final DashboardDefinitionUpdateDto dashboardUpdate = convertToUpdateDto(updatedDashboard);
    dashboardUpdate.setLastModifier(userId);
    dashboardUpdate.setLastModified(LocalDateUtil.getCurrentDateTime());
    return dashboardUpdate;
  }

  private DashboardDefinitionUpdateDto convertToUpdateDto(
      final DashboardDefinitionRestDto updatedDashboard) {
    final DashboardDefinitionUpdateDto updateDto = new DashboardDefinitionUpdateDto();
    updateDto.setName(updatedDashboard.getName());
    updateDto.setDescription(updatedDashboard.getDescription());
    updateDto.setTiles(updatedDashboard.getTiles());
    updateDto.setAvailableFilters(updatedDashboard.getAvailableFilters());
    updateDto.setRefreshRateSeconds(updatedDashboard.getRefreshRateSeconds());
    return updateDto;
  }

  public List<DashboardVariableFilterDto> extractDashboardVariableFilters(
      final List<DashboardFilterDto<?>> availableFilters) {
    return availableFilters.stream()
        .filter(DashboardVariableFilterDto.class::isInstance)
        .map(DashboardVariableFilterDto.class::cast)
        .toList();
  }
}
