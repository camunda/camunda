/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.dashboard;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.service.IdentityService;
import org.camunda.optimize.service.es.reader.DashboardReader;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.writer.DashboardWriter;
import org.camunda.optimize.service.relations.CollectionReferencingService;
import org.camunda.optimize.service.relations.ReportReferencingService;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
public class DashboardService implements ReportReferencingService, CollectionReferencingService {

  private final DashboardWriter dashboardWriter;
  private final DashboardReader dashboardReader;

  private final ReportService reportService;
  private final AuthorizedCollectionService collectionService;
  private final IdentityService identityService;
  private ReportReader reportReader;

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
    removeReportFromDashboards(reportDefinition.getId());
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportUpdate(final ReportDefinitionDto currentDefinition,
                                                                  final ReportDefinitionDto updateDefinition) {
    // NOOP
    return Collections.emptySet();
  }

  @Override
  public void handleReportUpdated(final String id, final ReportDefinitionDto updateDefinition) {
    //NOOP
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForCollectionDelete(final SimpleCollectionDefinitionDto definition) {
    return dashboardReader.findDashboardsForCollection(definition.getId()).stream()
      .map(dashboardDefinitionDto -> new ConflictedItemDto(
        dashboardDefinitionDto.getId(), ConflictedItemType.COLLECTION, dashboardDefinitionDto.getName()
      ))
      .collect(Collectors.toSet());
  }

  @Override
  public void handleCollectionDeleted(final SimpleCollectionDefinitionDto definition) {
    dashboardWriter.deleteDashboardsOfCollection(definition.getId());
  }

  public IdDto createNewDashboardAndReturnId(final String userId, final DashboardDefinitionDto dashboardDefinitionDto) {
    collectionService.verifyUserAuthorizedToEditCollectionResources(userId, dashboardDefinitionDto.getCollectionId());
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
    return copyAndMoveDashboard(dashboardId, userId, collectionId, name, new ConcurrentHashMap<>());
  }

  public IdDto copyAndMoveDashboard(final String dashboardId,
                                    final String userId,
                                    final String collectionId,
                                    final String name,
                                    final Map<String, String> uniqueReportCopies) {
    return copyAndMoveDashboard(dashboardId, userId, collectionId, name, uniqueReportCopies, false);
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
      dashboardDefinition.getReports().stream().sequential().forEach(reportLocationDto -> {
        if (IdGenerator.isValidId(reportLocationDto.getId())) {
          final String reportCopyId = uniqueReportCopies.computeIfAbsent(
            reportLocationDto.getId(),
            reportId -> {
              String newReportName = keepReportNames ? reportReader.getReport(reportId).getName() : null;

              return reportService.copyAndMoveReport(
                reportLocationDto.getId(),
                userId,
                collectionId,
                newReportName,
                uniqueReportCopies,
                keepReportNames
              ).getId();
            }
          );

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
    final AuthorizedDashboardDefinitionDto dashboardWithEditAuthorization = getDashboardWithEditAuthorization(
      dashboardId, userId
    );

    final DashboardDefinitionUpdateDto updateDto = convertToUpdateDto(updatedDashboard, userId);
    final String dashboardCollectionId = dashboardWithEditAuthorization.getDefinitionDto().getCollectionId();
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

    dashboardWriter.updateDashboard(updateDto, dashboardId);
  }

  public void deleteDashboard(final String dashboardId, final String userId) {
    getDashboardWithEditAuthorization(dashboardId, userId);

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

  private void removeReportFromDashboards(final String reportId) {
    dashboardWriter.removeReportFromDashboards(reportId);
  }

  private List<DashboardDefinitionDto> findFirstDashboardsForReport(final String reportId) {
    return dashboardReader.findFirstDashboardsForReport(reportId);
  }

  private boolean isSameCollection(final String newCollectionId, final String oldCollectionId) {
    return StringUtils.equals(newCollectionId, oldCollectionId);
  }

  private DashboardDefinitionUpdateDto convertToUpdateDto(final DashboardDefinitionDto updatedDashboard,
                                                          final String userId) {
    final DashboardDefinitionUpdateDto updateDto = new DashboardDefinitionUpdateDto();
    updateDto.setName(updatedDashboard.getName());
    updateDto.setReports(updatedDashboard.getReports());
    updateDto.setLastModifier(userId);
    updateDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    return updateDto;
  }

}
