/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.combined.configuration.CombinedReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedBy;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class ReportConflictIT {

  private static final String RANDOM_KEY = "someRandomKey";
  private static final String RANDOM_VERSION = "someRandomVersion";
  private static final String RANDOM_STRING = "something";

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule =
    new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @ParameterizedTest
  @MethodSource("provideForceParameterAsBoolean")
  public void updateSingleReportFailsWithConflictIfUsedInCombinedReportAndNotCombinableAnymoreWhenForceSet(Boolean force) {
    // given
    String firstSingleReportId = createAndStoreProcessReportWithDefinition(createRandomRawDataReport());
    String secondSingleReportId = createAndStoreProcessReportWithDefinition(createRandomRawDataReport());
    String combinedReportId = createNewCombinedReportWithDefinition(firstSingleReportId, secondSingleReportId);
    String[] expectedReportIds = new String[]{firstSingleReportId, secondSingleReportId, combinedReportId};
    String[] expectedConflictedItemIds = new String[]{combinedReportId};

    // when
    final SingleProcessReportDefinitionDto firstSingleReport =
      (SingleProcessReportDefinitionDto) getReport(firstSingleReportId);
    final SingleProcessReportDefinitionDto reportUpdate = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto groupByStartDateReport = ProcessReportDataBuilder
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

  @ParameterizedTest
  @MethodSource("provideForceParameterAsBoolean")
  public void updateSingleReportFailsWithConflictIfUsedInCombinedReportAndConfigurationNotCombinableAnymoreWhenForceSet(Boolean force) {
    // given
    String collectionId = createNewCollection();
    String singleReportId = createAndStoreProcessReportWithDefinition(collectionId, createRandomRawDataReport());
    String combinedReportId = createNewCombinedReportWithDefinition(singleReportId);
    String[] expectedConflictedItemIds = new String[]{combinedReportId};

    // when
    final SingleProcessReportDefinitionDto reportUpdate = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto userTaskReport = ProcessReportDataBuilder
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
    checkPrivateReportsStillExist(expectedConflictedItemIds);
    checkCombinedReportContainsSingleReports(combinedReportId, singleReportId);
  }

  @ParameterizedTest
  @MethodSource("provideForceParameterAsBoolean")
  public void updateSingleProcessReportFailsWithConflictIfUsedInAlertAndSuitableForAlertAnymoreWhenForceSet(Boolean force) {
    // given
    ProcessReportDataDto numberReport = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(RANDOM_KEY)
      .setProcessDefinitionVersion(RANDOM_VERSION)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    String collectionId = createNewCollection();
    String reportId = createAndStoreProcessReportWithDefinition(collectionId, numberReport);
    String alertForReport = createNewAlertForReport(reportId);
    String[] expectedConflictedItemIds = new String[]{alertForReport};

    // when
    final SingleProcessReportDefinitionDto singleReport =
      (SingleProcessReportDefinitionDto) getReport(reportId);
    final SingleProcessReportDefinitionDto reportUpdate = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto groupByStartDateReport = ProcessReportDataBuilder
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

  @ParameterizedTest
  @MethodSource("provideForceParameterAsBoolean")
  public void updateSingleDecisionReportFailsWithConflictIfUsedInAlertAndSuitableForAlertAnymoreWhenForceSet(Boolean force) {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    String collectionId = createNewCollection();
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

  @Test
  public void getSingleReportDeleteConflictsIfUsedByCombinedReport() {
    // given
    String firstSingleReportId = createProcessReport();
    String secondSingleReportId = createProcessReport();
    String firstCombinedReportId = createNewCombinedReportWithDefinition(firstSingleReportId, secondSingleReportId);
    String secondCombinedReportId = createNewCombinedReportWithDefinition(firstSingleReportId, secondSingleReportId);
    String[] expectedConflictedItemIds = {firstCombinedReportId, secondCombinedReportId};

    // when
    ConflictResponseDto conflictResponseDto = getReportDeleteConflicts(firstSingleReportId);

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.COMBINED_REPORT, expectedConflictedItemIds);
  }

  @ParameterizedTest
  @MethodSource("provideForceParameterAsBoolean")
  public void deleteSingleReportsFailsWithConflictIfUsedByCombinedReportWhenForceSet(Boolean force) {
    // given
    String firstSingleReportId = createProcessReport();
    String secondSingleReportId = createProcessReport();
    String firstCombinedReportId = createNewCombinedReportWithDefinition(firstSingleReportId, secondSingleReportId);
    String secondCombinedReportId = createNewCombinedReportWithDefinition(firstSingleReportId, secondSingleReportId);
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

  @ParameterizedTest
  @MethodSource("provideForceParameterAsBoolean")
  public void deleteSingleReportsFailsWithConflictIfUsedByAlertWhenForceSet(Boolean force) {
    // given
    String collectionId = createNewCollection();
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

  @ParameterizedTest
  @MethodSource("provideForceParameterAsBoolean")
  public void deleteSingleReportsFailsWithConflictIfUsedByDashboardWhenForceSet(Boolean force) {
    // given
    String reportId = createProcessReport();
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
        final DashboardDefinitionDto dashboard = embeddedOptimizeExtensionRule.getRequestExecutor()
          .buildGetDashboardRequest(dashboardId)
          .execute(DashboardDefinitionDto.class, 200);

        assertThat(dashboard, is(notNullValue()));
        assertThat(dashboard.getReports().size(), is(1));
        assertThat(
          dashboard.getReports().stream().anyMatch(
            reportLocationDto -> reportLocationDto.getId().equals(reportId)
          ),
          is(true)
        );
      });
  }

  private void checkCombinedReportContainsSingleReports(String combinedReportId, String... singleReportIds) {
    final ReportDefinitionDto combinedReport = getReport(combinedReportId);
    if (combinedReport instanceof CombinedReportDefinitionDto) {
      final CombinedReportDataDto dataDto = ((CombinedReportDefinitionDto) combinedReport).getData();
      assertThat(dataDto.getReportIds().size(), is(singleReportIds.length));
      assertThat(dataDto.getReportIds(), containsInAnyOrder(singleReportIds));
    }
  }

  private void checkConflictedItems(ConflictResponseDto conflictResponseDto,
                                    ConflictedItemType itemType,
                                    String[] expectedConflictedItemIds) {
    final Set<ConflictedItemDto> conflictedItemDtos = conflictResponseDto.getConflictedItems().stream()
      .filter(conflictedItemDto -> itemType.equals(conflictedItemDto.getType()))
      .collect(Collectors.toSet());

    assertThat(conflictedItemDtos.size(), is(expectedConflictedItemIds.length));
    assertThat(
      conflictedItemDtos.stream().map(ConflictedItemDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(expectedConflictedItemIds)
    );
  }

  private void checkAlertsStillExist(String[] expectedConflictedItemIds) {
    List<AlertDefinitionDto> alerts = getAllAlerts();
    assertThat(alerts.size(), is(expectedConflictedItemIds.length));
    assertThat(
      alerts.stream().map(AlertDefinitionDto::getId).collect(Collectors.toSet()),
      containsInAnyOrder(expectedConflictedItemIds)
    );
  }

  private void checkReportStillExistsInCollection(String reportId, String collectionId) {
    List<ReportDefinitionDto> reportDefinitionDtos =
      embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetReportsForCollectionRequest(collectionId)
      .executeAndReturnList(ReportDefinitionDto.class, 200);
    assertThat(
      reportDefinitionDtos.stream().map(ReportDefinitionDto::getId).collect(Collectors.toSet()),
      hasItems(reportId)
    );
  }

  private void checkPrivateReportsStillExist(String[] expectedReportIds) {
    List<ReportDefinitionDto> reports = getAllPrivateReports();
    assertThat(reports.size(), is(expectedReportIds.length));
    assertThat(
      reports.stream().map(ReportDefinitionDto::getId).collect(Collectors.toSet()),
      containsInAnyOrder(expectedReportIds)
    );
  }

  private String createNewDashboardAndAddReport(String reportId) {
    String id = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateDashboardRequest()
      .execute(IdDto.class, 200)
      .getId();

    final DashboardDefinitionDto dashboardUpdateDto = new DashboardDefinitionDto();
    final ReportLocationDto reportLocationDto = new ReportLocationDto();
    reportLocationDto.setId(reportId);
    dashboardUpdateDto.getReports().add(reportLocationDto);

    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdateDashboardRequest(id, dashboardUpdateDto)
      .execute();

    assertThat(response.getStatus(), is(204));

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
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateAlertRequest(alertCreationDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private List<AlertDefinitionDto> getAllAlerts() {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetAllAlertsRequest()
      .executeAndReturnList(AlertDefinitionDto.class, 200);
  }

  private String createNewCombinedReportWithDefinition(String... reportIds) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(createCombinedReportDefinition(reportIds))
      .execute(IdDto.class, 200)
      .getId();
  }

  private static CombinedReportDefinitionDto createCombinedReportDefinition(String... reportIds) {
    CombinedReportDefinitionDto combinedReportDefinitionDto = new CombinedReportDefinitionDto();
    CombinedReportDataDto combinedReportDataDto = new CombinedReportDataDto();
    combinedReportDataDto.setReports(
      Arrays.stream(reportIds).map(CombinedReportItemDto::new).collect(Collectors.toList())
    );
    combinedReportDataDto.setConfiguration(new CombinedReportConfigurationDto());
    combinedReportDefinitionDto.setData(combinedReportDataDto);
    return combinedReportDefinitionDto;
  }

  private ReportDefinitionDto getReport(String id) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetReportRequest(id)
      .execute(ReportDefinitionDto.class, 200);
  }

  private ConflictResponseDto getReportDeleteConflicts(String id) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetReportDeleteConflictsRequest(id)
      .execute(ConflictResponseDto.class, 200);
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
    return createSingleProcessReport(singleProcessReportDefinitionDto);
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
    return createDecisionReport(singleDecisionReportDefinitionDto);
  }

  private ConflictResponseDto deleteReportFailWithConflict(String reportId, Boolean force) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .execute(ConflictResponseDto.class, 409);

  }

  private String createSingleProcessReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private ConflictResponseDto updateReportFailWithConflict(String id,
                                                           SingleProcessReportDefinitionDto updatedReport,
                                                           Boolean force) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport, force)
      .execute(ConflictResponseDto.class, 409);
  }

  private ConflictResponseDto updateReportFailWithConflict(String id,
                                                           SingleDecisionReportDefinitionDto updatedReport,
                                                           Boolean force) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdateSingleDecisionReportRequest(id, updatedReport, force)
      .execute(ConflictResponseDto.class, 409);
  }

  private String createNewCollection() {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createProcessReport() {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createDecisionReport(SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private List<ReportDefinitionDto> getAllPrivateReports() {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetAllPrivateReportsRequest()
      .executeAndReturnList(ReportDefinitionDto.class, 200);
  }

  private static Object[] provideForceParameterAsBoolean() {
    return new Object[]{
      new Object[]{null},
      new Object[]{false},
    };
  }

  private ProcessReportDataDto createRandomRawDataReport() {
    return ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(ReportConflictIT.RANDOM_KEY)
      .setProcessDefinitionVersion(ReportConflictIT.RANDOM_VERSION)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
  }
}
