/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.report;

import com.google.common.collect.ImmutableSet;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizationType;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.UncombinableReportsException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeNonDefinitionScopeCompliantException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeNonTenantScopeCompliantException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeReportConflictException;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.relations.CollectionReferencingService;
import org.camunda.optimize.service.relations.ReportRelationService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.ReportAuthorizationService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.camunda.optimize.util.SuppressionConstants;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType.COMPLIANT;
import static org.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType.NON_DEFINITION_COMPLIANT;
import static org.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType.NON_TENANT_COMPLIANT;
import static org.camunda.optimize.service.util.BpmnModelUtil.extractProcessDefinitionName;
import static org.camunda.optimize.service.util.DmnModelUtil.extractDecisionDefinitionName;

@RequiredArgsConstructor
@Component
@Slf4j
public class ReportService implements CollectionReferencingService {

  private static final String DEFAULT_REPORT_NAME = "New Report";
  private static final String REPORT_NOT_IN_SAME_COLLECTION_ERROR_MESSAGE = "Either the report %s does not reside in " +
    "the same collection as the combined report or both are not private entities";

  private final ReportWriter reportWriter;
  private final ReportReader reportReader;
  private final ReportAuthorizationService reportAuthorizationService;
  private final ReportRelationService reportRelationService;
  private final AuthorizedCollectionService collectionService;
  private final AbstractIdentityService identityService;

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForCollectionDelete(final CollectionDefinitionDto definition) {
    return reportReader.getReportsForCollectionOmitXml(definition.getId()).stream()
      .map(reportDefinitionDto -> new ConflictedItemDto(
        reportDefinitionDto.getId(), ConflictedItemType.COLLECTION, reportDefinitionDto.getName()
      ))
      .collect(toSet());
  }

  @Override
  public void handleCollectionDeleted(final CollectionDefinitionDto definition) {
    List<ReportDefinitionDto> reportsToDelete = getReportsForCollection(definition.getId());
    for (ReportDefinitionDto reportDefinition : reportsToDelete) {
      reportRelationService.handleDeleted(reportDefinition);
    }
    reportWriter.deleteAllReportsOfCollection(definition.getId());
  }

  public IdResponseDto createNewSingleDecisionReport(final String userId,
                                                     final SingleDecisionReportDefinitionRequestDto definitionDto) {
    ensureCompliesWithCollectionScope(userId, definitionDto.getCollectionId(), definitionDto);
    validateReportDescription(definitionDto.getDescription());
    return createReport(
      userId, definitionDto, DecisionReportDataDto::new, reportWriter::createNewSingleDecisionReport
    );
  }

  public IdResponseDto createNewSingleProcessReport(final String userId,
                                                    final SingleProcessReportDefinitionRequestDto definitionDto) {
    ensureCompliesWithCollectionScope(userId, definitionDto.getCollectionId(), definitionDto);
    validateReportDescription(definitionDto.getDescription());
    Optional.ofNullable(definitionDto.getData())
      .ifPresent(data -> {
        ValidationHelper.validateProcessFilters(data.getFilter());
        Optional.ofNullable(data.getConfiguration())
          .ifPresent(config -> ValidationHelper.validateAggregationTypes(config.getAggregationTypes()));
      });
    return createReport(
      userId, definitionDto, ProcessReportDataDto::new, reportWriter::createNewSingleProcessReport
    );
  }

  public IdResponseDto createNewCombinedProcessReport(final String userId,
                                                      final CombinedReportDefinitionRequestDto combinedReportDefinitionDto) {
    validateReportDescription(combinedReportDefinitionDto.getDescription());
    verifyValidReportCombination(
      userId,
      combinedReportDefinitionDto.getCollectionId(),
      combinedReportDefinitionDto.getData()
    );
    return createReport(
      userId, combinedReportDefinitionDto, CombinedReportDataDto::new, reportWriter::createNewCombinedReport
    );
  }

  public ConflictResponseDto getReportDeleteConflictingItems(String userId, String reportId) {
    ReportDefinitionDto currentReportVersion = getReportDefinition(reportId, userId).getDefinitionDto();
    return new ConflictResponseDto(getConflictedItemsForDeleteReport(currentReportVersion));
  }

  public IdResponseDto copyReport(final String reportId, final String userId, final String newReportName) {
    final AuthorizedReportDefinitionResponseDto authorizedReportDefinition = getReportDefinition(reportId, userId);
    final ReportDefinitionDto oldReportDefinition = authorizedReportDefinition.getDefinitionDto();

    return copyAndMoveReport(reportId, userId, oldReportDefinition.getCollectionId(), newReportName, new HashMap<>());
  }

  public IdResponseDto copyAndMoveReport(final String reportId,
                                         final String userId,
                                         final String collectionId,
                                         final String newReportName) {
    return copyAndMoveReport(reportId, userId, collectionId, newReportName, new HashMap<>());
  }

  public List<IdResponseDto> getAllReportIdsInCollection(final String collectionId) {
    return reportReader.getReportsForCollectionOmitXml(collectionId)
      .stream()
      .map(report -> new IdResponseDto(report.getId()))
      .collect(toList());
  }

  public List<ReportDefinitionDto> getAllAuthorizedReportsForIds(final String userId, final List<String> reportIds) {
    return reportReader.getAllReportsForIdsOmitXml(reportIds)
      .stream()
      .filter(reportDefinitionDto -> reportAuthorizationService.isAuthorizedToReport(userId, reportDefinitionDto))
      .collect(toList());
  }

  public List<ReportDefinitionDto> getAllReportsForIds(final List<String> reportIds) {
    return reportReader.getAllReportsForIdsOmitXml(reportIds);
  }

  private IdResponseDto copyAndMoveReport(final String reportId,
                                          final String userId,
                                          final String collectionId,
                                          final String newReportName,
                                          final Map<String, String> existingReportCopies) {
    return copyAndMoveReport(reportId, userId, collectionId, newReportName, existingReportCopies, false);
  }

  /**
   * Note: The {@code existingReportCopies} {@code Map} might get modified in the context of this method, thus you
   * should not call this method from a context where this map is being modified already.
   * E.g. Don't call it inside a {@link Map#computeIfAbsent} block on that same map instance.
   */
  public IdResponseDto copyAndMoveReport(@NonNull final String reportId,
                                         @NonNull final String userId,
                                         final String collectionId,
                                         final String newReportName,
                                         @NonNull final Map<String, String> existingReportCopies,
                                         final boolean keepSubReportNames) {
    final AuthorizedReportDefinitionResponseDto authorizedReportDefinition = getReportDefinition(reportId, userId);
    final ReportDefinitionDto originalReportDefinition = authorizedReportDefinition.getDefinitionDto();
    if (isManagementOrInstantPreviewReport(originalReportDefinition)) {
      throw new OptimizeValidationException("Management and Instant Preview Reports cannot be copied");
    }
    collectionService.verifyUserAuthorizedToEditCollectionResources(userId, collectionId);

    final String oldCollectionId = originalReportDefinition.getCollectionId();
    final String newCollectionId = Objects.equals(oldCollectionId, collectionId) ? oldCollectionId : collectionId;

    final String newName = newReportName != null ? newReportName : originalReportDefinition.getName() + " – Copy";

    return copyAndMoveReport(
      originalReportDefinition, userId, newName, newCollectionId, existingReportCopies, keepSubReportNames
    );
  }

  public ReportDefinitionDto<ReportDataDto> getReportDefinition(final String reportId) {
    return reportReader.getReport(reportId)
      .orElseThrow(() -> new NotFoundException("Was not able to retrieve report with id [" + reportId + "]"
                                                 + "from Elasticsearch. Report does not exist."));
  }

  public AuthorizedReportDefinitionResponseDto getReportDefinition(final String reportId, final String userId) {
    final ReportDefinitionDto report = reportReader.getReport(reportId)
      .orElseThrow(() -> new NotFoundException("Was not able to retrieve report with id [" + reportId + "]"
                                                 + "from Elasticsearch. Report does not exist."));

    final RoleType currentUserRole = reportAuthorizationService.getAuthorizedRole(userId, report)
      .orElseThrow(() -> new ForbiddenException(String.format(
        "User [%s] is not authorized to access report [%s].", userId, reportId
      )));
    return new AuthorizedReportDefinitionResponseDto(report, currentUserRole);
  }

  public List<AuthorizedReportDefinitionResponseDto> findAndFilterPrivateReports(String userId) {
    List<ReportDefinitionDto> reports = reportReader.getAllPrivateReportsOmitXml();
    return filterAuthorizedReports(userId, reports)
      .stream()
      .sorted(Comparator.comparing(o -> ((AuthorizedReportDefinitionResponseDto) o).getDefinitionDto()
          .getLastModified())
                .reversed())
      .collect(toList());
  }

  public void deleteAllReportsForProcessDefinitionKey(String processDefinitionKey) {
    List<ReportDefinitionDto> reportsForDefinitionKey =
      getAllReportsForProcessDefinitionKeyOmitXml(processDefinitionKey);
    reportsForDefinitionKey.forEach(report -> removeReportAndAssociatedResources(report.getId(), report));
  }

  public List<ReportDefinitionDto> getAllReportsForProcessDefinitionKeyOmitXml(final String processDefinitionKey) {
    return reportReader.getAllReportsForProcessDefinitionKeyOmitXml(processDefinitionKey);
  }

  public List<AuthorizedReportDefinitionResponseDto> findAndFilterReports(String userId, String collectionId) {
    // verify user is authorized to access collection
    collectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId);

    List<ReportDefinitionDto> reportsInCollection = reportReader.getReportsForCollectionOmitXml(collectionId);
    return filterAuthorizedReports(userId, reportsInCollection);
  }

  private List<ReportDefinitionDto> getReportsForCollection(final String collectionId) {
    return reportReader.getReportsForCollectionOmitXml(collectionId);
  }

  public void updateCombinedProcessReport(final String userId,
                                          final String combinedReportId,
                                          final CombinedReportDefinitionRequestDto updatedReport) {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());
    validateReportDescription(updatedReport.getDescription());

    final ReportDefinitionDto currentReportVersion = getReportDefinition(combinedReportId, userId).getDefinitionDto();
    final AuthorizedReportDefinitionResponseDto authorizedCombinedReport =
      getReportWithEditAuthorization(userId, currentReportVersion);
    final String combinedReportCollectionId = authorizedCombinedReport.getDefinitionDto().getCollectionId();
    validateEntityEditorAuthorization(combinedReportCollectionId, userId);

    final CombinedProcessReportDefinitionUpdateDto reportUpdate =
      convertToCombinedProcessReportUpdate(updatedReport, userId);

    final CombinedReportDataDto data = reportUpdate.getData();
    verifyValidReportCombination(userId, combinedReportCollectionId, data);
    reportWriter.updateCombinedReport(reportUpdate);
  }

  public void updateSingleProcessReport(String reportId,
                                        SingleProcessReportDefinitionRequestDto updatedReport,
                                        String userId,
                                        boolean force) {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());
    ValidationHelper.validateProcessFilters(updatedReport.getData().getFilter());
    validateReportDescription(updatedReport.getDescription());
    Optional.ofNullable(updatedReport.getData().getConfiguration())
      .ifPresent(config -> ValidationHelper.validateAggregationTypes(config.getAggregationTypes()));

    final SingleProcessReportDefinitionRequestDto currentReportVersion = getSingleProcessReportDefinition(
      reportId, userId
    );
    if (isManagementOrInstantPreviewReport(currentReportVersion)) {
      throw new OptimizeValidationException("Management and Instant Preview Reports cannot be updated");
    }
    getReportWithEditAuthorization(userId, currentReportVersion);
    ensureCompliesWithCollectionScope(userId, currentReportVersion.getCollectionId(), updatedReport);
    validateEntityEditorAuthorization(currentReportVersion.getCollectionId(), userId);

    final SingleProcessReportDefinitionUpdateDto reportUpdate = convertToSingleProcessReportUpdate(
      updatedReport, userId
    );

    if (!force) {
      checkForUpdateConflictsOnSingleProcessDefinition(currentReportVersion, updatedReport);
    }

    reportRelationService.handleUpdated(reportId, updatedReport);
    if (semanticsForCombinedReportChanged(currentReportVersion, updatedReport)) {
      reportWriter.removeSingleReportFromCombinedReports(reportId);
    }
    reportWriter.updateSingleProcessReport(reportUpdate);
  }

  public void updateDefinitionXmlOfProcessReports(final String definitionKey, final String definitionXml) {
    reportWriter.updateProcessDefinitionXmlForProcessReportsWithKey(definitionKey, definitionXml);
  }

  public void updateSingleDecisionReport(String reportId,
                                         SingleDecisionReportDefinitionRequestDto updatedReport,
                                         String userId,
                                         boolean force) {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());
    validateReportDescription(updatedReport.getDescription());
    final SingleDecisionReportDefinitionRequestDto currentReportVersion =
      getSingleDecisionReportDefinition(reportId, userId);
    getReportWithEditAuthorization(userId, currentReportVersion);
    ensureCompliesWithCollectionScope(userId, currentReportVersion.getCollectionId(), updatedReport);
    validateEntityEditorAuthorization(currentReportVersion.getCollectionId(), userId);

    final SingleDecisionReportDefinitionUpdateDto reportUpdate = convertToSingleDecisionReportUpdate(
      updatedReport, userId
    );

    if (!force) {
      checkForUpdateConflictsOnSingleDecisionDefinition(currentReportVersion, updatedReport);
    }

    reportRelationService.handleUpdated(reportId, updatedReport);
    reportWriter.updateSingleDecisionReport(reportUpdate);
  }

  public void deleteReport(final String reportId) {
    final ReportDefinitionDto<?> reportDefinition = getReportOrFail(reportId);
    if (isManagementOrInstantPreviewReport(reportDefinition)) {
      throw new OptimizeValidationException("Management and Instant Preview Reports cannot be deleted manually");
    }
    removeReportAndAssociatedResources(reportId, reportDefinition);
  }

  public void deleteManagementOrInstantPreviewReport(final String reportId) {
    final ReportDefinitionDto<?> reportDefinition = getReportOrFail(reportId);
    removeReportAndAssociatedResources(reportId, reportDefinition);
  }

  public void deleteReportAsUser(final String userId, final String reportId, final boolean force) {
    final ReportDefinitionDto<?> reportDefinition = getReportOrFail(reportId);
    if (isManagementOrInstantPreviewReport(reportDefinition)) {
      throw new OptimizeValidationException("Management and Instant Preview Reports cannot be deleted");
    }
    getReportWithEditAuthorization(userId, reportDefinition);
    validateEntityEditorAuthorization(reportDefinition.getCollectionId(), userId);

    if (!force) {
      final Set<ConflictedItemDto> conflictedItems = getConflictedItemsForDeleteReport(reportDefinition);

      if (!conflictedItems.isEmpty()) {
        throw new OptimizeReportConflictException(conflictedItems);
      }
    }
    removeReportAndAssociatedResources(reportId, reportDefinition);
  }

  private void verifyValidReportCombination(final String userId, final String combinedReportCollectionId,
                                            final CombinedReportDataDto data) {
    if (data.getReportIds() != null && !data.getReportIds().isEmpty()) {
      final List<SingleProcessReportDefinitionRequestDto> reportsOfCombinedReport = reportReader
        .getAllSingleProcessReportsForIdsOmitXml(data.getReportIds());

      final List<String> reportIds = data.getReportIds();
      if (reportsOfCombinedReport.size() != reportIds.size()) {
        final List<String> reportIdsFetched = reportsOfCombinedReport.stream()
          .map(SingleProcessReportDefinitionRequestDto::getId).collect(toList());
        final List<String> invalidReportIds = reportIds.stream()
          .filter(reportIdsFetched::contains)
          .collect(toList());
        throw new OptimizeValidationException(String.format(
          "The following report IDs could not be found or are not single process reports: %s", invalidReportIds));
      }

      final SingleProcessReportDefinitionRequestDto firstReport = reportsOfCombinedReport.get(0);
      final boolean allReportsCanBeCombined = reportsOfCombinedReport.stream()
        .peek(report -> {
          final ReportDefinitionDto reportDefinition = getReportDefinition(report.getId(), userId).getDefinitionDto();

          if (!Objects.equals(combinedReportCollectionId, reportDefinition.getCollectionId())) {
            throw new BadRequestException(String.format(
              REPORT_NOT_IN_SAME_COLLECTION_ERROR_MESSAGE, reportDefinition.getId()
            ));
          }
        })
        .noneMatch(report -> semanticsForCombinedReportChanged(firstReport, report));
      if (allReportsCanBeCombined) {
        final ProcessVisualization visualization = firstReport.getData() == null
          ? null
          : firstReport.getData().getVisualization();
        data.setVisualization(visualization);
      } else {
        final String errorMessage =
          String.format(
            "Can't create or update combined report. " +
              "The following report ids are not combinable: [%s]",
            data.getReportIds()
          );
        log.error(errorMessage);
        throw new UncombinableReportsException(errorMessage);
      }
    }
  }

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  private <T extends ReportDataDto> ReportDefinitionDto<T> getReportOrFail(final String reportId) {
    return reportReader.getReport(reportId)
      .orElseThrow(() -> new NotFoundException(
        "Was not able to retrieve report with id [" + reportId + "] from Elasticsearch. Report does not exist."
      ));
  }

  private void removeReportAndAssociatedResources(final String reportId, final ReportDefinitionDto reportDefinition) {
    reportRelationService.handleDeleted(reportDefinition);
    if (reportDefinition.isCombined()) {
      reportWriter.deleteCombinedReport(reportId);
    } else {
      reportWriter.removeSingleReportFromCombinedReports(reportId);
      reportWriter.deleteSingleReport(reportId);
    }
  }

  private <T extends ReportDefinitionDto<RD>, RD extends ReportDataDto> IdResponseDto createReport(
    final String userId,
    final T reportDefinition,
    final Supplier<RD> defaultDataProvider,
    final CreateReportMethod<RD> createReportMethod) {

    final Optional<T> optionalProvidedDefinition = Optional.ofNullable(reportDefinition);
    final String collectionId = optionalProvidedDefinition
      .map(ReportDefinitionDto::getCollectionId)
      .orElse(null);
    validateEntityEditorAuthorization(collectionId, userId);
    collectionService.verifyUserAuthorizedToEditCollectionResources(userId, collectionId);

    return createReportMethod.create(
      userId,
      optionalProvidedDefinition.map(ReportDefinitionDto::getData).orElse(defaultDataProvider.get()),
      optionalProvidedDefinition.map(ReportDefinitionDto::getName).orElse(DEFAULT_REPORT_NAME),
      optionalProvidedDefinition.map(ReportDefinitionDto::getDescription).orElse(null),
      collectionId
    );
  }

  private AuthorizedReportDefinitionResponseDto getReportWithEditAuthorization(final String userId,
                                                                               final ReportDefinitionDto reportDefinition) {
    final Optional<RoleType> authorizedRole = reportAuthorizationService.getAuthorizedRole(userId, reportDefinition);
    return authorizedRole
      .filter(roleType -> roleType.ordinal() >= RoleType.EDITOR.ordinal())
      .map(role -> new AuthorizedReportDefinitionResponseDto(reportDefinition, role))
      .orElseThrow(() -> new ForbiddenException(
        "User [" + userId + "] is not authorized to edit report [" + reportDefinition.getName() + "]."
      ));
  }

  public Set<ConflictedItemDto> getConflictedItemsFromReportDefinition(String userId, String reportId) {
    ReportDefinitionDto reportDefinitionDto = getReportDefinition(reportId, userId).getDefinitionDto();
    return getConflictedItemsForDeleteReport(reportDefinitionDto);
  }

  public void validateReportDescription(final String reportDescription) {
    if (reportDescription != null) {
      if (reportDescription.length() > 400) {
        throw new OptimizeValidationException("Report descriptions cannot be greater than 400 characters");
      } else if (reportDescription.isEmpty()) {
        throw new OptimizeValidationException("Report descriptions cannot be non-null and empty");
      }
    }
  }

  private Set<ConflictedItemDto> mapCombinedReportsToConflictingItems(List<CombinedReportDefinitionRequestDto> combinedReportDtos) {
    return combinedReportDtos.stream()
      .map(combinedReportDto -> new ConflictedItemDto(
        combinedReportDto.getId(), ConflictedItemType.COMBINED_REPORT, combinedReportDto.getName()
      ))
      .collect(toSet());
  }

  private IdResponseDto copyAndMoveReport(final ReportDefinitionDto originalReportDefinition,
                                          final String userId,
                                          final String newReportName,
                                          final String newCollectionId,
                                          final Map<String, String> existingReportCopies,
                                          final boolean keepSubReportNames) {
    final String oldCollectionId = originalReportDefinition.getCollectionId();
    validateEntityEditorAuthorization(oldCollectionId, userId);

    if (!originalReportDefinition.isCombined()) {
      switch (originalReportDefinition.getReportType()) {
        case PROCESS:
          SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
            (SingleProcessReportDefinitionRequestDto) originalReportDefinition;
          ensureCompliesWithCollectionScope(
            userId,
            newCollectionId,
            singleProcessReportDefinitionDto
          );
          return reportWriter.createNewSingleProcessReport(
            userId,
            singleProcessReportDefinitionDto.getData(),
            newReportName,
            originalReportDefinition.getDescription(),
            newCollectionId
          );
        case DECISION:
          SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto =
            (SingleDecisionReportDefinitionRequestDto) originalReportDefinition;
          ensureCompliesWithCollectionScope(
            userId,
            newCollectionId,
            singleDecisionReportDefinitionDto
          );
          return reportWriter.createNewSingleDecisionReport(
            userId,
            singleDecisionReportDefinitionDto.getData(),
            newReportName,
            originalReportDefinition.getDescription(),
            newCollectionId
          );
        default:
          throw new IllegalStateException("Unsupported reportType: " + originalReportDefinition.getReportType());
      }
    } else {
      CombinedReportDefinitionRequestDto combinedReportDefinition =
        (CombinedReportDefinitionRequestDto) originalReportDefinition;
      return copyAndMoveCombinedReport(
        userId,
        newReportName,
        newCollectionId,
        oldCollectionId,
        combinedReportDefinition,
        existingReportCopies,
        keepSubReportNames
      );
    }
  }

  private IdResponseDto copyAndMoveCombinedReport(final String userId,
                                                  final String newName,
                                                  final String newCollectionId,
                                                  final String oldCollectionId,
                                                  final CombinedReportDefinitionRequestDto oldCombinedReportDef,
                                                  final Map<String, String> existingReportCopies,
                                                  final boolean keepSubReportNames) {
    final CombinedReportDataDto oldCombinedReportData = oldCombinedReportDef.getData();
    final CombinedReportDataDto newCombinedReportData = new CombinedReportDataDto(
      oldCombinedReportData.getConfiguration(),
      oldCombinedReportData.getVisualization(),
      oldCombinedReportData.getReports()
    );

    if (!StringUtils.equals(newCollectionId, oldCollectionId)) {
      final List<CombinedReportItemDto> newReports = new ArrayList<>();
      oldCombinedReportData
        .getReports()
        .stream()
        .sequential()
        .peek(report -> ensureCompliesWithCollectionScope(userId, newCollectionId, report.getId()))
        .forEach(combinedReportItemDto -> {
          final String originalSubReportId = combinedReportItemDto.getId();
          final ReportDefinitionDto report = reportReader.getReport(originalSubReportId)
            .orElseThrow(() -> new NotFoundException("Was not able to retrieve report with id [" + originalSubReportId + "]"
                                                       + "from Elasticsearch. Report does not exist."));

          final String reportName = keepSubReportNames ? report.getName() : null;
          String subReportCopyId = existingReportCopies.get(originalSubReportId);
          if (subReportCopyId == null) {
            subReportCopyId = copyAndMoveReport(
              originalSubReportId, userId, newCollectionId, reportName, existingReportCopies
            ).getId();
            existingReportCopies.put(originalSubReportId, subReportCopyId);
          }
          newReports.add(
            combinedReportItemDto.toBuilder().id(subReportCopyId).color(combinedReportItemDto.getColor()).build()
          );
        });
      newCombinedReportData.setReports(newReports);
    }

    return reportWriter.createNewCombinedReport(
      userId,
      newCombinedReportData,
      newName,
      oldCombinedReportDef.getDescription(),
      newCollectionId
    );
  }

  private Set<ConflictedItemDto> getConflictedItemsForDeleteReport(ReportDefinitionDto reportDefinition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();
    if (!reportDefinition.isCombined()) {
      conflictedItems.addAll(
        mapCombinedReportsToConflictingItems(reportReader.getCombinedReportsForSimpleReport(reportDefinition.getId()))
      );
    }
    conflictedItems.addAll(reportRelationService.getConflictedItemsForDeleteReport(reportDefinition));
    return conflictedItems;
  }

  public void ensureCompliesWithCollectionScope(final String userId, final String collectionId, final String reportId) {
    final ReportDefinitionDto reportDefinition = reportReader.getReport(reportId)
      .orElseThrow(() -> new NotFoundException("Was not able to retrieve report with id [" + reportId + "]"
                                                 + "from Elasticsearch. Report does not exist."));

    if (!reportDefinition.isCombined()) {
      SingleReportDefinitionDto<?> singleProcessReportDefinitionDto =
        (SingleReportDefinitionDto<?>) reportDefinition;
      ensureCompliesWithCollectionScope(userId, collectionId, singleProcessReportDefinitionDto);
    }
  }

  public void ensureCompliesWithCollectionScope(final List<ReportDataDefinitionDto> definitions,
                                                final DefinitionType definitionType,
                                                final CollectionDefinitionDto collection) {
    definitions.forEach(definitionDto -> ensureCompliesWithCollectionScope(
      definitionDto.getKey(), definitionDto.getTenantIds(), definitionType, collection
    ));
  }

  public boolean isReportAllowedForCollectionScope(final SingleReportDefinitionDto<?> report,
                                                   final CollectionDefinitionDto collection) {
    return report.getData().getDefinitions().stream()
      .allMatch(definitionDto -> COMPLIANT.equals(getScopeComplianceForReport(
        definitionDto.getKey(), definitionDto.getTenantIds(), report.getReportType().toDefinitionType(), collection
      )));
  }

  private void ensureCompliesWithCollectionScope(final CollectionDefinitionDto collection,
                                                 final SingleReportDefinitionDto<?> report) {
    ensureCompliesWithCollectionScope(report.getData().getDefinitions(), report.getDefinitionType(), collection);
  }

  private void ensureCompliesWithCollectionScope(final String userId,
                                                 final String collectionId,
                                                 final SingleReportDefinitionDto<?> definition) {
    if (collectionId == null) {
      return;
    }

    final CollectionDefinitionDto collection =
      collectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId).getDefinitionDto();

    ensureCompliesWithCollectionScope(collection, definition);
  }

  private void ensureCompliesWithCollectionScope(final String definitionKey,
                                                 final List<String> tenantIds,
                                                 final DefinitionType definitionType,
                                                 final CollectionDefinitionDto collection) {
    final ScopeComplianceType complianceLevel = getScopeComplianceForReport(
      definitionKey, tenantIds, definitionType, collection
    );
    if (NON_TENANT_COMPLIANT.equals(complianceLevel)) {
      final ConflictedItemDto conflictedItemDto = new ConflictedItemDto(
        collection.getId(),
        ConflictedItemType.COLLECTION,
        collection.getName()
      );
      throw new OptimizeNonTenantScopeCompliantException(ImmutableSet.of(conflictedItemDto));
    } else if (NON_DEFINITION_COMPLIANT.equals(complianceLevel)) {
      final ConflictedItemDto conflictedItemDto = new ConflictedItemDto(
        collection.getId(),
        ConflictedItemType.COLLECTION,
        collection.getName()
      );
      throw new OptimizeNonDefinitionScopeCompliantException(ImmutableSet.of(conflictedItemDto));
    }
  }

  private void validateEntityEditorAuthorization(final String collectionId, final String userId) {
    if (collectionId == null && !identityService.getUserAuthorizations(userId).contains(AuthorizationType.ENTITY_EDITOR)) {
      throw new ForbiddenException("User is not an authorized entity editor");
    }
  }

  private ScopeComplianceType getScopeComplianceForReport(final String definitionKey,
                                                          final List<String> tenantIds,
                                                          final DefinitionType definitionType,
                                                          final CollectionDefinitionDto collection) {
    if (definitionKey == null) {
      return COMPLIANT;
    }

    final List<ScopeComplianceType> compliances = collection.getData()
      .getScope()
      .stream()
      .map(scope -> scope.getComplianceType(definitionType, definitionKey, tenantIds))
      .collect(toList());

    boolean scopeCompliant =
      compliances.stream().anyMatch(compliance -> compliance.equals(COMPLIANT));
    if (scopeCompliant) {
      return COMPLIANT;
    }
    boolean definitionCompliantButNonTenantCompliant =
      compliances.stream().anyMatch(compliance -> compliance.equals(ScopeComplianceType.NON_TENANT_COMPLIANT));
    if (definitionCompliantButNonTenantCompliant) {
      return ScopeComplianceType.NON_TENANT_COMPLIANT;
    }
    return ScopeComplianceType.NON_DEFINITION_COMPLIANT;
  }

  private void checkForUpdateConflictsOnSingleProcessDefinition(SingleProcessReportDefinitionRequestDto currentReportVersion,
                                                                SingleProcessReportDefinitionRequestDto reportUpdateDto) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();

    final String reportId = currentReportVersion.getId();

    if (semanticsForCombinedReportChanged(currentReportVersion, reportUpdateDto)) {
      conflictedItems.addAll(
        mapCombinedReportsToConflictingItems(reportReader.getCombinedReportsForSimpleReport(reportId))
      );
    }

    conflictedItems.addAll(
      reportRelationService.getConflictedItemsForUpdatedReport(currentReportVersion, reportUpdateDto)
    );

    if (!conflictedItems.isEmpty()) {
      throw new OptimizeReportConflictException(conflictedItems);
    }
  }

  private void checkForUpdateConflictsOnSingleDecisionDefinition(SingleDecisionReportDefinitionRequestDto currentReportVersion,
                                                                 SingleDecisionReportDefinitionRequestDto reportUpdateDto) {
    final Set<ConflictedItemDto> conflictedItems = reportRelationService.getConflictedItemsForUpdatedReport(
      currentReportVersion,
      reportUpdateDto
    );

    if (!conflictedItems.isEmpty()) {
      throw new OptimizeReportConflictException(conflictedItems);
    }
  }

  private boolean semanticsForCombinedReportChanged(SingleProcessReportDefinitionRequestDto firstReport,
                                                    SingleProcessReportDefinitionRequestDto secondReport) {
    boolean result = false;
    if (firstReport.getData() != null) {
      ProcessReportDataDto oldData = firstReport.getData();
      ProcessReportDataDto newData = secondReport.getData();
      result = !newData.isCombinable(oldData);
    }
    return result;
  }

  private SingleProcessReportDefinitionUpdateDto convertToSingleProcessReportUpdate(
    final SingleProcessReportDefinitionRequestDto updatedReport,
    final String userId) {
    SingleProcessReportDefinitionUpdateDto reportUpdate = new SingleProcessReportDefinitionUpdateDto();
    copyDefinitionMetaDataToUpdate(updatedReport, reportUpdate, userId);
    reportUpdate.setData(updatedReport.getData());
    final String xml = reportUpdate.getData().getConfiguration().getXml();
    if (xml != null) {
      final String definitionKey = reportUpdate.getData().getDefinitionKey();
      reportUpdate.getData().setProcessDefinitionName(
        extractProcessDefinitionName(definitionKey, xml).orElse(definitionKey)
      );
    }
    return reportUpdate;
  }

  private SingleDecisionReportDefinitionUpdateDto convertToSingleDecisionReportUpdate(
    final SingleDecisionReportDefinitionRequestDto updatedReport,
    final String userId) {

    SingleDecisionReportDefinitionUpdateDto reportUpdate = new SingleDecisionReportDefinitionUpdateDto();
    copyDefinitionMetaDataToUpdate(updatedReport, reportUpdate, userId);
    reportUpdate.setData(updatedReport.getData());
    final String xml = reportUpdate.getData().getConfiguration().getXml();
    if (xml != null) {
      final String definitionKey = reportUpdate.getData().getDecisionDefinitionKey();
      reportUpdate.getData().setDecisionDefinitionName(
        extractDecisionDefinitionName(definitionKey, xml).orElse(definitionKey)
      );
    }
    return reportUpdate;
  }

  private CombinedProcessReportDefinitionUpdateDto convertToCombinedProcessReportUpdate(
    final CombinedReportDefinitionRequestDto updatedReport,
    final String userId) {
    CombinedProcessReportDefinitionUpdateDto reportUpdate = new CombinedProcessReportDefinitionUpdateDto();
    copyDefinitionMetaDataToUpdate(updatedReport, reportUpdate, userId);
    reportUpdate.setData(updatedReport.getData());
    return reportUpdate;
  }

  private SingleProcessReportDefinitionRequestDto getSingleProcessReportDefinition(String reportId,
                                                                                   String userId) {
    SingleProcessReportDefinitionRequestDto report = reportReader.getSingleProcessReportOmitXml(reportId)
      .orElseThrow(() -> new NotFoundException("Single process report with id [" + reportId + "] does not exist!"));

    if (!reportAuthorizationService.isAuthorizedToReport(userId, report)) {
      throw new ForbiddenException("User [" + userId + "] is not authorized to access or edit report [" +
                                     report.getName() + "].");
    }
    return report;
  }

  private SingleDecisionReportDefinitionRequestDto getSingleDecisionReportDefinition(String reportId,
                                                                                     String userId) {
    SingleDecisionReportDefinitionRequestDto report = reportReader.getSingleDecisionReportOmitXml(reportId)
      .orElseThrow(() -> new NotFoundException("Single decision report with id [" + reportId + "] does not exist!"));

    if (!reportAuthorizationService.isAuthorizedToReport(userId, report)) {
      throw new ForbiddenException("User [" + userId + "] is not authorized to access or edit report [" +
                                     report.getName() + "].");
    }
    return report;
  }

  public Set<String> filterAuthorizedReportIds(final String userId,
                                               final Set<String> reportIds) {
    final List<ReportDefinitionDto> reports = reportReader.getAllReportsForIdsOmitXml(new ArrayList<>(reportIds));
    return filterAuthorizedReports(userId, reports).stream()
      .map(report -> report.getDefinitionDto().getId())
      .collect(toSet());
  }

  private List<AuthorizedReportDefinitionResponseDto> filterAuthorizedReports(String userId,
                                                                              List<ReportDefinitionDto> reports) {
    return reports.stream()
      .map(report -> Pair.of(report, reportAuthorizationService.getAuthorizedRole(userId, report)))
      .filter(reportAndRole -> reportAndRole.getValue().isPresent())
      .map(reportAndRole -> new AuthorizedReportDefinitionResponseDto(
        reportAndRole.getKey(),
        reportAndRole.getValue().get()
      ))
      .collect(toList());
  }

  private static void copyDefinitionMetaDataToUpdate(ReportDefinitionDto from,
                                                     ReportDefinitionUpdateDto to,
                                                     String userId) {
    to.setId(from.getId());
    to.setName(from.getName());
    to.setDescription(from.getDescription());
    to.setLastModifier(userId);
    to.setLastModified(from.getLastModified());
  }

  private boolean isManagementOrInstantPreviewReport(final ReportDefinitionDto<?> reportDefinition) {
    return reportDefinition instanceof SingleProcessReportDefinitionRequestDto
      && (((SingleProcessReportDefinitionRequestDto) reportDefinition).getData().isManagementReport()
      || ((SingleProcessReportDefinitionRequestDto) reportDefinition).getData().isInstantPreviewReport());
  }

  @FunctionalInterface
  private interface CreateReportMethod<RD extends ReportDataDto> {
    IdResponseDto create(String userId, RD reportData, String reportName, String description, String collectionId);
  }

}