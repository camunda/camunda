/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.dashboard;

import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.DashboardFilterType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.BooleanVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.service.IdentityService;
import org.camunda.optimize.service.es.reader.DashboardReader;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.writer.DashboardWriter;
import org.camunda.optimize.service.relations.CollectionReferencingService;
import org.camunda.optimize.service.relations.DashboardRelationService;
import org.camunda.optimize.service.relations.ReportReferencingService;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.variable.ProcessVariableService;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
public class DashboardService implements ReportReferencingService, CollectionReferencingService {

  private final DashboardWriter dashboardWriter;
  private final DashboardReader dashboardReader;

  private final ProcessVariableService processVariableService;
  private final ReportService reportService;
  private final AuthorizedCollectionService collectionService;
  private final IdentityService identityService;
  private final ReportReader reportReader;
  private final DashboardRelationService dashboardRelationService;

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportDelete(final ReportDefinitionDto reportDefinition) {
    return findFirstDashboardsForReport(reportDefinition.getId()).stream()
      .map(dashboardDefinitionDto -> new ConflictedItemDto(
        dashboardDefinitionDto.getId(), ConflictedItemType.DASHBOARD, dashboardDefinitionDto.getName()
      ))
      .collect(Collectors.toSet());
  }

  @Override
  public void handleReportDeleted(final ReportDefinitionDto reportDefinition) {
    if (reportDefinition instanceof SingleProcessReportDefinitionDto) {
      final List<ProcessVariableNameResponseDto> varNamesForReportToRemove =
        processVariableService.getVariableNamesForReportDefinitions(
          Collections.singletonList((SingleProcessReportDefinitionDto) reportDefinition));
      removeVariableFiltersFromDashboardsIfUnavailable(varNamesForReportToRemove, reportDefinition.getId());
    }
    removeReportFromDashboards(reportDefinition.getId());
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportUpdate(final ReportDefinitionDto currentDefinition,
                                                                  final ReportDefinitionDto updateDefinition) {
    // NOOP
    return Collections.emptySet();
  }

  @Override
  public void handleReportUpdated(final String reportId, final ReportDefinitionDto updateDefinition) {
    final ReportDefinitionDto existingReport = reportReader.getReport(reportId);
    if (existingReport instanceof SingleProcessReportDefinitionDto) {
      final SingleProcessReportDefinitionDto existingReportDefinition =
        (SingleProcessReportDefinitionDto) existingReport;
      final SingleProcessReportDefinitionDto updateReportDefinition =
        (SingleProcessReportDefinitionDto) updateDefinition;
      final List<ProcessVariableNameResponseDto> availableVariableNamesForExistingReport =
        processVariableService.getVariableNamesForReportDefinitions(Collections.singletonList(existingReportDefinition));
      final List<ProcessVariableNameResponseDto> availableVariableNamesForUpdatedReport =
        processVariableService.getVariableNamesForReportDefinitions(Collections.singletonList(updateReportDefinition));
      availableVariableNamesForExistingReport.removeAll(availableVariableNamesForUpdatedReport);
      removeVariableFiltersFromDashboardsIfUnavailable(availableVariableNamesForExistingReport, reportId);
    }
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForCollectionDelete(final CollectionDefinitionDto definition) {
    return dashboardReader.findDashboardsForCollection(definition.getId()).stream()
      .map(dashboardDefinitionDto -> new ConflictedItemDto(
        dashboardDefinitionDto.getId(), ConflictedItemType.COLLECTION, dashboardDefinitionDto.getName()
      ))
      .collect(Collectors.toSet());
  }

  @Override
  public void handleCollectionDeleted(final CollectionDefinitionDto definition) {
    dashboardWriter.deleteDashboardsOfCollection(definition.getId());
  }

  public IdDto createNewDashboardAndReturnId(final String userId, final DashboardDefinitionDto dashboardDefinitionDto) {
    collectionService.verifyUserAuthorizedToEditCollectionResources(userId, dashboardDefinitionDto.getCollectionId());
    validateDashboardFilters(userId, dashboardDefinitionDto);
    return dashboardWriter.createNewDashboard(userId, dashboardDefinitionDto);
  }

  public IdDto copyDashboard(final String dashboardId, final String userId, final String name) {
    final AuthorizedDashboardDefinitionDto authorizedDashboard = getDashboardDefinition(dashboardId, userId);
    final DashboardDefinitionDto dashboardDefinition = authorizedDashboard.getDefinitionDto();

    String newDashboardName = name != null ? name : dashboardDefinition.getName() + " – Copy";
    return copyAndMoveDashboard(dashboardId, userId, dashboardDefinition.getCollectionId(), newDashboardName);
  }

  public IdDto copyAndMoveDashboard(final String dashboardId,
                                    final String userId,
                                    final String collectionId,
                                    final String name) {
    return copyAndMoveDashboard(dashboardId, userId, collectionId, name, new HashMap<>(), false);
  }

  public IdDto copyAndMoveDashboard(final String dashboardId,
                                    final String userId,
                                    final String collectionId,
                                    final String name,
                                    final Map<String, String> uniqueReportCopies,
                                    final boolean keepReportNames) {
    final AuthorizedDashboardDefinitionDto authorizedDashboard = getDashboardDefinition(dashboardId, userId);
    final DashboardDefinitionDto dashboardDefinition = authorizedDashboard.getDefinitionDto();

    collectionService.verifyUserAuthorizedToEditCollectionResources(userId, collectionId);

    final List<ReportLocationDto> newDashboardReports = new ArrayList<>(dashboardDefinition.getReports());

    if (!isSameCollection(collectionId, dashboardDefinition.getCollectionId())) {
      newDashboardReports.clear();
      containingReportsComplyWithNewCollectionScope(userId, collectionId, dashboardDefinition);
      dashboardDefinition.getReports().stream().sequential().forEach(reportLocationDto -> {
        final String originalReportId = reportLocationDto.getId();
        if (IdGenerator.isValidId(originalReportId)) {
          String reportCopyId = uniqueReportCopies.get(originalReportId);
          if (reportCopyId == null) {
            final String newReportName = keepReportNames ? reportReader.getReport(originalReportId).getName() : null;
            reportCopyId = reportService.copyAndMoveReport(
              originalReportId, userId, collectionId, newReportName, uniqueReportCopies, keepReportNames
            ).getId();
            uniqueReportCopies.put(originalReportId, reportCopyId);
          }

          newDashboardReports.add(
            reportLocationDto.toBuilder().id(reportCopyId).configuration(reportLocationDto.getConfiguration()).build()
          );
        } else {
          newDashboardReports.add(reportLocationDto);
        }
      });
    }

    String newDashboardName = name != null ? name : dashboardDefinition.getName() + " – Copy";
    DashboardDefinitionDto newDashboardDefinitionDto = new DashboardDefinitionDto();
    newDashboardDefinitionDto.setCollectionId(collectionId);
    newDashboardDefinitionDto.setName(newDashboardName);
    newDashboardDefinitionDto.setReports(newDashboardReports);
    return dashboardWriter.createNewDashboard(userId, newDashboardDefinitionDto);
  }

  private void removeVariableFiltersFromDashboardsIfUnavailable(final List<ProcessVariableNameResponseDto> filters,
                                                                final String reportId) {
    final List<DashboardDefinitionDto> dashboardsForReport = dashboardReader.findDashboardsForReport(reportId);
    dashboardsForReport
      .stream()
      .filter(dashboard -> !CollectionUtils.isEmpty(dashboard.getAvailableFilters()) &&
        dashboard.getAvailableFilters()
          .stream()
          .anyMatch(filter -> DashboardFilterType.VARIABLE.equals(filter.getType())))
      .forEach(dashboard -> {
        final List<String> otherReportIdsInDashboard = dashboard.getReports()
          .stream()
          .map(ReportLocationDto::getId)
          .filter(reportInDashboardId -> !reportId.equals(reportInDashboardId))
          .collect(Collectors.toList());
        final List<SingleProcessReportDefinitionDto> allReportsForIdsOmitXml =
          reportReader.getAllReportsForIdsOmitXml(otherReportIdsInDashboard)
            .stream()
            .filter(SingleProcessReportDefinitionDto.class::isInstance)
            .map(SingleProcessReportDefinitionDto.class::cast)
            .collect(Collectors.toList());
        final List<ProcessVariableNameResponseDto> varNamesForReportsToRemain =
          processVariableService.getVariableNamesForReportDefinitions(allReportsForIdsOmitXml);
        final List<DashboardFilterDto> filtersToRemove = dashboard.getAvailableFilters().stream()
          .filter(dashboardFilter -> DashboardFilterType.VARIABLE.equals(dashboardFilter.getType()))
          .filter(variableFilter -> {
            final VariableFilterDataDto filterData = variableFilter.getData();
            final ProcessVariableNameResponseDto processVariableForFilter =
              new ProcessVariableNameResponseDto(filterData.getName(), filterData.getType());
            return !varNamesForReportsToRemain.contains(processVariableForFilter) &&
              filters.contains(processVariableForFilter);
          })
          .collect(Collectors.toList());
        if (!filtersToRemove.isEmpty()) {
          dashboard.getAvailableFilters().removeAll(filtersToRemove);
          dashboardWriter.updateDashboard(convertToUpdateDto(dashboard), dashboard.getId());
        }
      });
  }

  private void containingReportsComplyWithNewCollectionScope(final String userId, final String collectionId,
                                                             final DashboardDefinitionDto dashboardDefinition) {
    dashboardDefinition
      .getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .filter(IdGenerator::isValidId)
      .forEach(reportId -> reportService.ensureCompliesWithCollectionScope(userId, collectionId, reportId));
  }

  public AuthorizedDashboardDefinitionDto getDashboardDefinition(final String dashboardId,
                                                                 final String userId) {
    final DashboardDefinitionDto dashboard = getDashboardDefinitionAsService(dashboardId);
    RoleType currentUserRole = null;
    if (dashboard.getCollectionId() != null) {
      currentUserRole = collectionService.getUsersCollectionResourceRole(userId, dashboard.getCollectionId())
        .orElse(null);
    } else if (dashboard.getOwner().equals(userId)) {
      currentUserRole = RoleType.EDITOR;
    } else if (identityService.isSuperUserIdentity(userId)) {
      currentUserRole = RoleType.EDITOR;
    }

    if (currentUserRole == null) {
      throw new ForbiddenException(String.format(
        "User [%s] is not authorized to access dashboard [%s].", userId, dashboardId
      ));
    }

    return new AuthorizedDashboardDefinitionDto(currentUserRole, dashboard);
  }

  public DashboardDefinitionDto getDashboardDefinitionAsService(final String dashboardId) {
    return dashboardReader.getDashboard(dashboardId);
  }

  public void updateDashboard(final DashboardDefinitionDto updatedDashboard, final String userId) {
    final String dashboardId = updatedDashboard.getId();
    final AuthorizedDashboardDefinitionDto dashboardWithEditAuthorization =
      getDashboardWithEditAuthorization(dashboardId, userId);

    final DashboardDefinitionUpdateDto updateDto = convertToUpdateDtoWithModifier(updatedDashboard, userId);
    final String dashboardCollectionId = dashboardWithEditAuthorization.getDefinitionDto().getCollectionId();
    validateDashboardFilters(userId, updatedDashboard);
    updateDto.getReports().forEach(reportLocationDto -> {
      if (IdGenerator.isValidId(reportLocationDto.getId())) {
        final ReportDefinitionDto reportDefinition =
          reportService.getReportDefinition(reportLocationDto.getId(), userId).getDefinitionDto();
        if (!Objects.equals(dashboardCollectionId, reportDefinition.getCollectionId())) {
          throw new BadRequestException(String.format(
            "Report %s does not reside in the same collection as the dashboard %s or are both not private entities",
            reportDefinition.getId(),
            dashboardId
          ));
        }
      }
    });

    dashboardRelationService.handleUpdated(updatedDashboard);
    dashboardWriter.updateDashboard(updateDto, dashboardId);
  }

  public void deleteDashboard(final String dashboardId, final String userId) {
    final DashboardDefinitionDto dashboardDefinitionDto =
      getDashboardWithEditAuthorization(dashboardId, userId).getDefinitionDto();

    dashboardRelationService.handleDeleted(dashboardDefinitionDto);
    dashboardWriter.deleteDashboard(dashboardId);
  }

  private AuthorizedDashboardDefinitionDto getDashboardWithEditAuthorization(final String dashboardId,
                                                                             final String userId) {
    final AuthorizedDashboardDefinitionDto authorizedDashboardDefinition =
      getDashboardDefinition(dashboardId, userId);
    if (authorizedDashboardDefinition.getCurrentUserRole().ordinal() < RoleType.EDITOR.ordinal()) {
      throw new ForbiddenException(String.format(
        "User [%s] is not authorized to edit dashboard [%s].", userId, dashboardId
      ));
    }
    return authorizedDashboardDefinition;
  }

  private void validateDashboardFilters(final String userId, final DashboardDefinitionDto dashboardDefinitionDto) {
    final List<DashboardFilterDto> availableFilters = dashboardDefinitionDto.getAvailableFilters();
    if (!CollectionUtils.isEmpty(availableFilters)) {
      if (availableFilters.stream().anyMatch(filter -> filter.getType() == null)) {
        throw new BadRequestException("Dashboard Filters cannot have a null type");
      }
      final Map<DashboardFilterType, List<DashboardFilterDto>> filtersByType = availableFilters
        .stream()
        .collect(Collectors.groupingBy(DashboardFilterDto::getType));
      validateNonVariableTypeFilters(filtersByType);
      validateVariableFilters(filtersByType);
      validateVariableFiltersExistInReports(userId, dashboardDefinitionDto, availableFilters);
    }
  }

  private void validateVariableFiltersExistInReports(final String userId,
                                                     final DashboardDefinitionDto dashboardDefinitionDto,
                                                     final List<DashboardFilterDto> availableFilters) {
    final List<String> reportIdsInDashboard = dashboardDefinitionDto.getReports().stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());
    final Map<String, List<VariableType>> possibleVarTypesByName = processVariableService.getVariableNamesForReports(
      userId,
      reportIdsInDashboard
    ).stream().collect(
      Collectors.groupingBy(
        ProcessVariableNameResponseDto::getName,
        Collectors.mapping(ProcessVariableNameResponseDto::getType, Collectors.toList())
      )
    );

    final List<DashboardFilterDto> invalidFilters = availableFilters.stream()
      .filter(isInvalidVariableFilter(possibleVarTypesByName))
      .collect(Collectors.toList());
    if (!invalidFilters.isEmpty()) {
      throw new BadRequestException(String.format(
        "The following variable filter names/types do not exist in any report in dashboard: [%s]",
        invalidFilters
      ));
    }
  }

  private Predicate<DashboardFilterDto> isInvalidVariableFilter(final Map<String, List<VariableType>> possibleVarTypesByName) {
    return filter -> {
      if (DashboardFilterType.VARIABLE.equals(filter.getType())) {
        final List<VariableType> typesByName = possibleVarTypesByName.get(filter.getData().getName());
        return typesByName == null || !typesByName.contains(filter.getData().getType());
      }
      return false;
    };
  }

  private void validateVariableFilters(final Map<DashboardFilterType, List<DashboardFilterDto>> filtersByType) {
    final List<DashboardFilterDto> variableFilters = filtersByType.get(DashboardFilterType.VARIABLE);
    if (variableFilters != null) {
      variableFilters.forEach(variableFilter -> {
        final VariableFilterDataDto<?> filterData = variableFilter.getData();
        if (filterData == null) {
          throw new BadRequestException("Variable dashboard filters require additional data");
        }
        final VariableType variableType = filterData.getType();
        if (variableType.equals(VariableType.DATE)) {
          if (filterData.getData() != null) {
            throw new BadRequestException(String.format(
              "Filter data cannot be supplied for %s variable type variable filters", variableType.toString()));
          }
        } else if (variableType.equals(VariableType.BOOLEAN)) {
          final BooleanVariableFilterSubDataDto booleanFilterData =
            (BooleanVariableFilterSubDataDto) variableFilter.getData().getData();
          if (booleanFilterData.getValues() != null) {
            throw new BadRequestException(String.format(
              "Filter data cannot be supplied for %s variable type variable filters", variableType.toString()));
          }
        }
      });
    }
  }

  private void validateNonVariableTypeFilters(final Map<DashboardFilterType, List<DashboardFilterDto>> filtersByType) {
    filtersByType.entrySet().stream()
      .filter(filterType -> !filterType.getKey().equals(DashboardFilterType.VARIABLE))
      .forEach(byType -> {
        if (byType.getValue().size() > 1) {
          throw new BadRequestException(String.format(
            "There can only be one of each non-variable filter types. Filters of type %s supplied: %s",
            byType.getKey(),
            byType.getValue()
          )
          );
        }
        final List<DashboardFilterDto> filtersWithData = byType.getValue()
          .stream()
          .filter(filter -> filter.getData() != null)
          .collect(Collectors.toList());
        if (!filtersWithData.isEmpty()) {
          throw new BadRequestException(String.format(
            "Filters %s supplied additional data but are not a variable filter", filtersWithData)
          );
        }
      });
  }

  private void removeReportFromDashboards(final String reportId) {
    dashboardWriter.removeReportFromDashboards(reportId);
  }

  private List<DashboardDefinitionDto> findFirstDashboardsForReport(final String reportId) {
    return dashboardReader.findDashboardsForReport(reportId);
  }

  private boolean isSameCollection(final String newCollectionId, final String oldCollectionId) {
    return StringUtils.equals(newCollectionId, oldCollectionId);
  }

  private DashboardDefinitionUpdateDto convertToUpdateDtoWithModifier(final DashboardDefinitionDto updatedDashboard,
                                                                      final String userId) {
    final DashboardDefinitionUpdateDto dashboardUpdate = convertToUpdateDto(updatedDashboard);
    dashboardUpdate.setLastModifier(userId);
    dashboardUpdate.setLastModified(LocalDateUtil.getCurrentDateTime());
    return dashboardUpdate;
  }

  private DashboardDefinitionUpdateDto convertToUpdateDto(final DashboardDefinitionDto updatedDashboard) {
    final DashboardDefinitionUpdateDto updateDto = new DashboardDefinitionUpdateDto();
    updateDto.setName(updatedDashboard.getName());
    updateDto.setReports(updatedDashboard.getReports());
    updateDto.setAvailableFilters(updatedDashboard.getAvailableFilters());
    return updateDto;
  }

}
