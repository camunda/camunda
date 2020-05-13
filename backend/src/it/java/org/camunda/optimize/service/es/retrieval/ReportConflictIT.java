/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedBy;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;

public class ReportConflictIT extends AbstractIT {

  private static final String RANDOM_VERSION = "someRandomVersion";
  private static final String RANDOM_STRING = "something";

  @ParameterizedTest(name = "update single report fails with conflict if used in combined report and not combinable " +
    "anymore when force set to {0}")
  @MethodSource("provideForceParameterAsBoolean")
  public void updateSingleReportFailsWithConflictIfUsedInCombinedReportAndNotCombinableAnymoreWhenForceSet(Boolean force) {
    // given
    String firstSingleReportId = createAndStoreProcessReportWithDefinition(createRandomRawDataReport());
    String secondSingleReportId = createAndStoreProcessReportWithDefinition(createRandomRawDataReport());
    String combinedReportId = reportClient.createCombinedReport(
      null,
      Arrays.asList(firstSingleReportId, secondSingleReportId)
    );
    String[] expectedReportIds = new String[]{firstSingleReportId, secondSingleReportId, combinedReportId};
    String[] expectedConflictedItemIds = new String[]{combinedReportId};

    // when
    final SingleProcessReportDefinitionDto firstSingleReport =
      (SingleProcessReportDefinitionDto) reportClient.getReportById(firstSingleReportId);
    final SingleProcessReportDefinitionDto reportUpdate = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto groupByStartDateReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(firstSingleReport.getData().getProcessDefinitionKey())
      .setProcessDefinitionVersions(firstSingleReport.getData().getDefinitionVersions())
      .setDateInterval(GroupByDateUnit.DAY)
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();
    reportUpdate.setData(groupByStartDateReport);
    ConflictResponseDto conflictResponseDto = updateReportFailWithConflict(
      firstSingleReportId,
      reportUpdate,
      force
    );

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.COMBINED_REPORT, expectedConflictedItemIds);
    checkPrivateReportsStillExist(expectedReportIds);
    checkCombinedReportContainsSingleReports(combinedReportId, firstSingleReportId, secondSingleReportId);
  }

  @ParameterizedTest(name = "update single report fails with conflict if used in combined report and configuration " +
    "not combinable anymore when force set to {0}")
  @MethodSource("provideForceParameterAsBoolean")
  public void updateSingleReportFailsWithConflictIfUsedInCombinedReportAndConfigurationNotCombinableAnymoreWhenForceSet(Boolean force) {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();
    String singleReportId = createAndStoreProcessReportWithDefinition(collectionId, createRandomRawDataReport());
    String combinedReportId = reportClient.createCombinedReport(collectionId, Arrays.asList(singleReportId));
    String[] expectedConflictedItemIds = new String[]{combinedReportId};

    // when
    final SingleProcessReportDefinitionDto reportUpdate = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto userTaskReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_CANDIDATE_BY_USER_TASK)
      .build();
    userTaskReport.getConfiguration().setDistributedBy(DistributedBy.USER_TASK);
    reportUpdate.setData(userTaskReport);
    ConflictResponseDto conflictResponseDto = updateReportFailWithConflict(
      singleReportId,
      reportUpdate,
      force
    );

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.COMBINED_REPORT, expectedConflictedItemIds);
    checkReportStillExistsInCollection(singleReportId, collectionId);
    checkReportStillExistsInCollection(combinedReportId, collectionId);
    checkCombinedReportContainsSingleReports(combinedReportId, singleReportId);
  }

  @ParameterizedTest(name = "update single process report fails with conflict if used in alert and suitable for alert" +
    " anymore when force set to {0}")
  @MethodSource("provideForceParameterAsBoolean")
  public void updateSingleProcessReportFailsWithConflictIfUsedInAlertAndSuitableForAlertAnymoreWhenForceSet(Boolean force) {
    // given
    ProcessReportDataDto numberReport = createProcessReport(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE);
    String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();
    String reportId = createAndStoreProcessReportWithDefinition(collectionId, numberReport);
    String alertForReport = createNewAlertForReport(reportId);
    String[] expectedConflictedItemIds = new String[]{alertForReport};

    // when
    final SingleProcessReportDefinitionDto singleReport =
      (SingleProcessReportDefinitionDto) reportClient.getReportById(reportId);
    final SingleProcessReportDefinitionDto reportUpdate = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto groupByStartDateReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(singleReport.getData().getProcessDefinitionKey())
      .setProcessDefinitionVersions(singleReport.getData().getDefinitionVersions())
      .setDateInterval(GroupByDateUnit.DAY)
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();
    reportUpdate.setData(groupByStartDateReport);
    ConflictResponseDto conflictResponseDto = updateReportFailWithConflict(reportId, reportUpdate, force);

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.ALERT, expectedConflictedItemIds);
    checkReportStillExistsInCollection(reportId, collectionId);
    checkAlertsStillExist(expectedConflictedItemIds);
  }

  @ParameterizedTest(name = "update single decision report fails with conflict if sued in alert and suitable for " +
    "alert anymore when force set to {0}")
  @MethodSource("provideForceParameterAsBoolean")
  public void updateSingleDecisionReportFailsWithConflictIfUsedInAlertAndSuitableForAlertAnymoreWhenForceSet(Boolean force) {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    String collectionId = collectionClient.createNewCollection();
    String reportId = createAndStoreDefaultDecisionReportDefinition(collectionId, reportData);
    String alertForReport = createNewAlertForReport(reportId);
    String[] expectedConflictedItemIds = new String[]{alertForReport};

    // when
    final SingleDecisionReportDefinitionDto reportUpdate = new SingleDecisionReportDefinitionDto();
    reportData = DecisionReportDataBuilder.create()
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();
    reportUpdate.setData(reportData);
    ConflictResponseDto conflictResponseDto = updateReportFailWithConflict(reportId, reportUpdate, force);

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.ALERT, expectedConflictedItemIds);
    checkReportStillExistsInCollection(reportId, collectionId);
    checkAlertsStillExist(expectedConflictedItemIds);
  }

  @ParameterizedTest
  @MethodSource("provideForceParameterAsBoolean")
  public void updateSingleReportFailsWithConflictIfUsedInCombinedReportAndConfigurationNotNoneWhenForceSet(Boolean force) {
    // given
    String firstSingleReportId = createAndStoreProcessReportWithDefinition(
      createProcessReport(ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_USER_TASK));
    String secondSingleReportId = createAndStoreProcessReportWithDefinition(
      createProcessReport(ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_USER_TASK));
    String combinedReportId = reportClient.createCombinedReport(
      null,
      Arrays.asList(firstSingleReportId, secondSingleReportId)
    );
    String[] expectedReportIds = new String[]{firstSingleReportId, secondSingleReportId, combinedReportId};
    String[] expectedConflictedItemIds = new String[]{combinedReportId};

    // when
    final SingleProcessReportDefinitionDto firstSingleReport =
      (SingleProcessReportDefinitionDto) reportClient.getReportById(firstSingleReportId);
    firstSingleReport.getData().getConfiguration().setDistributedBy(DistributedBy.ASSIGNEE);
    ConflictResponseDto conflictResponseDto = updateReportFailWithConflict(
      firstSingleReportId,
      firstSingleReport,
      force
    );

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.COMBINED_REPORT, expectedConflictedItemIds);
    checkPrivateReportsStillExist(expectedReportIds);
    checkCombinedReportContainsSingleReports(combinedReportId, firstSingleReportId, secondSingleReportId);
  }

  @Test
  public void getSingleReportDeleteConflictsIfUsedByCombinedReport() {
    // given
    String firstSingleReportId = reportClient.createEmptySingleProcessReport();
    String secondSingleReportId = reportClient.createEmptySingleProcessReport();
    String firstCombinedReportId = reportClient.createCombinedReport(
      null,
      Arrays.asList(
        firstSingleReportId,
        secondSingleReportId
      )
    );
    String secondCombinedReportId = reportClient.createCombinedReport(
      null,
      Arrays.asList(
        firstSingleReportId,
        secondSingleReportId
      )
    );
    String[] expectedConflictedItemIds = {firstCombinedReportId, secondCombinedReportId};

    // when
    ConflictResponseDto conflictResponseDto = reportClient.getReportDeleteConflicts(firstSingleReportId);

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.COMBINED_REPORT, expectedConflictedItemIds);
  }

  @ParameterizedTest(name = "delete single reports fails with conflict if used by combined report when force set to {0}")
  @MethodSource("provideForceParameterAsBoolean")
  public void deleteSingleReportsFailsWithConflictIfUsedByCombinedReportWhenForceSet(Boolean force) {
    // given
    String firstSingleReportId = reportClient.createEmptySingleProcessReport();
    String secondSingleReportId = reportClient.createEmptySingleProcessReport();
    String firstCombinedReportId = reportClient.createCombinedReport(
      null,
      Arrays.asList(
        firstSingleReportId,
        secondSingleReportId
      )
    );
    String secondCombinedReportId = reportClient.createCombinedReport(
      null,
      Arrays.asList(
        firstSingleReportId,
        secondSingleReportId
      )
    );
    String[] expectedReportIds = {
      firstSingleReportId, secondSingleReportId, firstCombinedReportId, secondCombinedReportId
    };
    String[] expectedConflictedItemIds = {firstCombinedReportId, secondCombinedReportId};

    // when
    ConflictResponseDto conflictResponseDto = deleteReportFailWithConflict(firstSingleReportId, force);

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.COMBINED_REPORT, expectedConflictedItemIds);
    checkPrivateReportsStillExist(expectedReportIds);
    checkCombinedReportContainsSingleReports(firstCombinedReportId, firstSingleReportId, secondSingleReportId);
    checkCombinedReportContainsSingleReports(secondCombinedReportId, firstSingleReportId, secondSingleReportId);
  }

  @ParameterizedTest(name = "delete single reports fails with conflict if used by alert when force set to {0}")
  @MethodSource("provideForceParameterAsBoolean")
  public void deleteSingleReportsFailsWithConflictIfUsedByAlertWhenForceSet(Boolean force) {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();
    String reportId = createAndStoreProcessReportWithDefinition(collectionId, createRandomRawDataReport());
    String firstAlertForReport = createNewAlertForReport(reportId);
    String secondAlertForReport = createNewAlertForReport(reportId);
    String[] expectedConflictedItemIds = {firstAlertForReport, secondAlertForReport};

    // when
    ConflictResponseDto conflictResponseDto = deleteReportFailWithConflict(reportId, force);

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.ALERT, expectedConflictedItemIds);
    checkReportStillExistsInCollection(reportId, collectionId);
    checkAlertsStillExist(expectedConflictedItemIds);
  }

  @ParameterizedTest(name = "delete single reports fails with conflict if sued by dashboard when force set to {0}")
  @MethodSource("provideForceParameterAsBoolean")
  public void deleteSingleReportsFailsWithConflictIfUsedByDashboardWhenForceSet(Boolean force) {
    // given
    String reportId = reportClient.createEmptySingleProcessReport();
    String firstDashboardId = createNewDashboardAndAddReport(reportId);
    String secondDashboardId = createNewDashboardAndAddReport(reportId);
    String[] expectedReportIds = {reportId};
    String[] expectedConflictedItemIds = {firstDashboardId, secondDashboardId};

    // when
    ConflictResponseDto conflictResponseDto = deleteReportFailWithConflict(reportId, force);

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.DASHBOARD, expectedConflictedItemIds);
    checkPrivateReportsStillExist(expectedReportIds);
    checkDashboardsStillContainReport(expectedConflictedItemIds, reportId);
  }


  private void checkDashboardsStillContainReport(String[] dashboardIds, String reportId) {
    Arrays.stream(dashboardIds)
      .forEach(dashboardId -> {
        final DashboardDefinitionDto dashboard = embeddedOptimizeExtension.getRequestExecutor()
          .buildGetDashboardRequest(dashboardId)
          .execute(DashboardDefinitionDto.class, Response.Status.OK.getStatusCode());
        assertThat(dashboard).isNotNull();
        assertThat(dashboard.getReports()).extracting(ReportLocationDto::getId).contains(reportId);
      });
  }

  private void checkCombinedReportContainsSingleReports(String combinedReportId, String... singleReportIds) {
    final ReportDefinitionDto combinedReport = reportClient.getReportById(combinedReportId);
    if (combinedReport instanceof CombinedReportDefinitionDto) {
      final CombinedReportDataDto dataDto = ((CombinedReportDefinitionDto) combinedReport).getData();
      assertThat(dataDto.getReportIds())
        .containsExactlyInAnyOrder(singleReportIds);
    }
  }

  private void checkConflictedItems(ConflictResponseDto conflictResponseDto,
                                    ConflictedItemType itemType,
                                    String[] expectedConflictedItemIds) {
    final Set<ConflictedItemDto> conflictedItemDtos = conflictResponseDto.getConflictedItems().stream()
      .filter(conflictedItemDto -> itemType.equals(conflictedItemDto.getType()))
      .collect(Collectors.toSet());

    assertThat(conflictedItemDtos)
      .hasSize(expectedConflictedItemIds.length)
      .extracting(ConflictedItemDto::getId)
      .containsExactlyInAnyOrder(expectedConflictedItemIds);
  }

  private void checkAlertsStillExist(String[] expectedConflictedItemIds) {
    List<AlertDefinitionDto> alerts = alertClient.getAllAlerts();
    assertThat(alerts)
      .hasSize(expectedConflictedItemIds.length)
      .extracting(AlertDefinitionDto::getId)
      .containsExactlyInAnyOrder(expectedConflictedItemIds);
  }

  private void checkReportStillExistsInCollection(String reportId, String collectionId) {
    List<AuthorizedReportDefinitionDto> reportDefinitionDtos = collectionClient.getReportsForCollection(collectionId);

    assertThat(reportDefinitionDtos)
      .extracting(AuthorizedReportDefinitionDto::getDefinitionDto)
      .extracting(ReportDefinitionDto::getId)
      .contains(reportId);
  }

  private void checkPrivateReportsStillExist(String[] expectedReportIds) {
    List<AuthorizedReportDefinitionDto> reports = reportClient.getAllReportsAsUser();
    assertThat(reports)
      .hasSize(expectedReportIds.length)
      .extracting(AuthorizedReportDefinitionDto::getDefinitionDto)
      .extracting(ReportDefinitionDto::getId)
      .containsExactlyInAnyOrder(expectedReportIds);
  }

  private String createNewDashboardAndAddReport(String reportId) {
    String id = dashboardClient.createEmptyDashboard(null);
    dashboardClient.updateDashboardWithReports(id, Collections.singletonList(reportId));
    return id;
  }

  private String createNewAlertForReport(String reportId) {
    final AlertCreationDto alertCreationDto = new AlertCreationDto();
    AlertInterval interval = new AlertInterval();
    interval.setUnit("Seconds");
    interval.setValue(1);
    alertCreationDto.setCheckInterval(interval);
    alertCreationDto.setThreshold(0);
    alertCreationDto.setThresholdOperator(">");
    alertCreationDto.setEmail("test@camunda.com");
    alertCreationDto.setName("test alert");
    alertCreationDto.setReportId(reportId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateAlertRequest(alertCreationDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private String createAndStoreProcessReportWithDefinition(ProcessReportDataDto reportDataViewRawAsTable) {
    return createAndStoreProcessReportWithDefinition(null, reportDataViewRawAsTable);
  }

  private String createAndStoreProcessReportWithDefinition(String collectionId,
                                                           ProcessReportDataDto reportDataViewRawAsTable) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(reportDataViewRawAsTable);
    singleProcessReportDefinitionDto.setId(RANDOM_STRING);
    singleProcessReportDefinitionDto.setLastModifier(RANDOM_STRING);
    singleProcessReportDefinitionDto.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleProcessReportDefinitionDto.setCreated(someDate);
    singleProcessReportDefinitionDto.setLastModified(someDate);
    singleProcessReportDefinitionDto.setOwner(RANDOM_STRING);
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private String createAndStoreDefaultDecisionReportDefinition(String collectionId, DecisionReportDataDto reportData) {
    SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto = new SingleDecisionReportDefinitionDto();
    singleDecisionReportDefinitionDto.setData(reportData);
    singleDecisionReportDefinitionDto.setId(RANDOM_STRING);
    singleDecisionReportDefinitionDto.setLastModifier(RANDOM_STRING);
    singleDecisionReportDefinitionDto.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleDecisionReportDefinitionDto.setCreated(someDate);
    singleDecisionReportDefinitionDto.setLastModified(someDate);
    singleDecisionReportDefinitionDto.setOwner(RANDOM_STRING);
    singleDecisionReportDefinitionDto.setCollectionId(collectionId);
    return reportClient.createSingleDecisionReport(singleDecisionReportDefinitionDto);
  }

  private ConflictResponseDto deleteReportFailWithConflict(String reportId, Boolean force) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

  }

  private ConflictResponseDto updateReportFailWithConflict(String id,
                                                           SingleProcessReportDefinitionDto updatedReport,
                                                           Boolean force) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport, force)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());
  }

  private ConflictResponseDto updateReportFailWithConflict(String id,
                                                           SingleDecisionReportDefinitionDto updatedReport,
                                                           Boolean force) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateSingleDecisionReportRequest(id, updatedReport, force)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());
  }

  private static Stream<Boolean> provideForceParameterAsBoolean() {
    return Stream.of(null, false);
  }

  private ProcessReportDataDto createProcessReport(final ProcessReportDataType processReportDataType) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEFAULT_DEFINITION_KEY)
      .setProcessDefinitionVersion(RANDOM_VERSION)
      .setTenantIds(DEFAULT_TENANTS)
      .setReportDataType(processReportDataType)
      .build();
  }

  private ProcessReportDataDto createRandomRawDataReport() {
    return createProcessReport(ProcessReportDataType.RAW_DATA);
  }
}
