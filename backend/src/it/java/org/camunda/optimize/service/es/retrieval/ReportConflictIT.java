/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
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
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

@RunWith(JUnitParamsRunner.class)
public class ReportConflictIT {

  private static final String RANDOM_KEY = "someRandomKey";
  private static final String RANDOM_VERSION = "someRandomVersion";
  private static final String RANDOM_STRING = "something";

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  @Parameters(source = ForceParameterProvider.class)
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
    checkReportsStillExist(expectedReportIds);
    checkCombinedReportContainsSingleReports(combinedReportId, firstSingleReportId, secondSingleReportId);
  }

  @Test
  @Parameters(source = ForceParameterProvider.class)
  public void updateSingleReportFailsWithConflictIfUsedInCombinedReportAndConfigurationNotCombinableAnymoreWhenForceSet(Boolean force) {
    // given
    String firstSingleReportId =
      createAndStoreProcessReportWithDefinition(createRandomRawDataReport());
    String combinedReportId = createNewCombinedReportWithDefinition(firstSingleReportId);
    String[] expectedReportIds = new String[]{firstSingleReportId, combinedReportId};
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
      firstSingleReportId,
      reportUpdate,
      force
    );

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.COMBINED_REPORT, expectedConflictedItemIds);
    checkReportsStillExist(expectedReportIds);
    checkCombinedReportContainsSingleReports(combinedReportId, firstSingleReportId);
  }

  @Test
  @Parameters(source = ForceParameterProvider.class)
  public void updateSingleProcessReportFailsWithConflictIfUsedInAlertAndSuitableForAlertAnymoreWhenForceSet(Boolean force) {
    // given
    ProcessReportDataDto numberReport = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(RANDOM_KEY)
      .setProcessDefinitionVersion(RANDOM_VERSION)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    String reportId = createAndStoreProcessReportWithDefinition(numberReport);
    String alertForReport = createNewAlertForReport(reportId);
    String[] expectedReportIds = new String[]{reportId};
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
    checkReportsStillExist(expectedReportIds);
    checkAlertsStillExist(expectedConflictedItemIds);
  }

  @Test
  @Parameters(source = ForceParameterProvider.class)
  public void updateSingleDecisionReportFailsWithConflictIfUsedInAlertAndSuitableForAlertAnymoreWhenForceSet(Boolean force) {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    String reportId = createAndStoreDefaultDecisionReportDefinition(reportData);
    String alertForReport = createNewAlertForReport(reportId);
    String[] expectedReportIds = new String[]{reportId};
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
    checkReportsStillExist(expectedReportIds);
    checkAlertsStillExist(expectedConflictedItemIds);
  }

  @Test
  public void getSingleReportDeleteConflictsIfUsedByCombinedReport() {
    // given
    String firstSingleReportId = addEmptyProcessReportToOptimize();
    String secondSingleReportId = addEmptyProcessReportToOptimize();
    String firstCombinedReportId = createNewCombinedReportWithDefinition(firstSingleReportId, secondSingleReportId);
    String secondCombinedReportId = createNewCombinedReportWithDefinition(firstSingleReportId, secondSingleReportId);
    String[] expectedConflictedItemIds = {firstCombinedReportId, secondCombinedReportId};

    // when
    ConflictResponseDto conflictResponseDto = getReportDeleteConflicts(firstSingleReportId);

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.COMBINED_REPORT, expectedConflictedItemIds);
  }

  @Test
  @Parameters(source = ForceParameterProvider.class)
  public void deleteSingleReportsFailsWithConflictIfUsedByCombinedReportWhenForceSet(Boolean force) {
    // given
    String firstSingleReportId = addEmptyProcessReportToOptimize();
    String secondSingleReportId = addEmptyProcessReportToOptimize();
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
    checkReportsStillExist(expectedReportIds);
    checkCombinedReportContainsSingleReports(firstCombinedReportId, firstSingleReportId, secondSingleReportId);
    checkCombinedReportContainsSingleReports(secondCombinedReportId, firstSingleReportId, secondSingleReportId);
  }

  @Test
  @Parameters(source = ForceParameterProvider.class)
  public void deleteSingleReportsFailsWithConflictIfUsedByAlertWhenForceSet(Boolean force) {
    // given
    String reportId = addEmptyProcessReportToOptimize();
    String firstAlertForReport = createNewAlertForReport(reportId);
    String secondAlertForReport = createNewAlertForReport(reportId);
    String[] expectedReportIds = {reportId};
    String[] expectedConflictedItemIds = {firstAlertForReport, secondAlertForReport};

    // when
    ConflictResponseDto conflictResponseDto = deleteReportFailWithConflict(reportId, force);

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.ALERT, expectedConflictedItemIds);
    checkReportsStillExist(expectedReportIds);
    checkAlertsStillExist(expectedConflictedItemIds);
  }

  @Test
  @Parameters(source = ForceParameterProvider.class)
  public void deleteSingleReportsFailsWithConflictIfUsedByDashboardWhenForceSet(Boolean force) {
    // given
    String reportId = addEmptyProcessReportToOptimize();
    String firstDashboardId = createNewDashboardAndAddReport(reportId);
    String secondDashboardId = createNewDashboardAndAddReport(reportId);
    String[] expectedReportIds = {reportId};
    String[] expectedConflictedItemIds = {firstDashboardId, secondDashboardId};

    // when
    ConflictResponseDto conflictResponseDto = deleteReportFailWithConflict(reportId, force);

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.DASHBOARD, expectedConflictedItemIds);
    checkReportsStillExist(expectedReportIds);
    checkDashboardsStillContainReport(expectedConflictedItemIds, reportId);
  }


  private void checkDashboardsStillContainReport(String[] dashboardIds, String reportId) {
    Arrays.stream(dashboardIds)
      .forEach(dashboardId -> {
        final DashboardDefinitionDto dashboard = embeddedOptimizeRule.getRequestExecutor()
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

  private void checkReportsStillExist(String[] expectedReportIds) {
    List<ReportDefinitionDto> reports = getAllReports();
    assertThat(reports.size(), is(expectedReportIds.length));
    assertThat(
      reports.stream().map(ReportDefinitionDto::getId).collect(Collectors.toSet()),
      containsInAnyOrder(expectedReportIds)
    );
  }

  private String createNewDashboardAndAddReport(String reportId) {
    String id = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequest()
      .execute(IdDto.class, 200)
      .getId();

    final DashboardDefinitionDto dashboardUpdateDto = new DashboardDefinitionDto();
    final ReportLocationDto reportLocationDto = new ReportLocationDto();
    reportLocationDto.setId(reportId);
    dashboardUpdateDto.getReports().add(reportLocationDto);

    Response response = embeddedOptimizeRule
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
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateAlertRequest(alertCreationDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private List<AlertDefinitionDto> getAllAlerts() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllAlertsRequest()
      .executeAndReturnList(AlertDefinitionDto.class, 200);
  }

  private String createNewCombinedReportWithDefinition(String... reportIds) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequestWithDefinition(createCombinedReportDefinition(reportIds))
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
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetReportRequest(id)
      .execute(ReportDefinitionDto.class, 200);
  }

  private ConflictResponseDto getReportDeleteConflicts(String id) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetReportDeleteConflictsRequest(id)
      .execute(ConflictResponseDto.class, 200);
  }

  private String createAndStoreProcessReportWithDefinition(ProcessReportDataDto reportDataViewRawAsTable) {
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportDataViewRawAsTable);
    report.setId(RANDOM_STRING);
    report.setLastModifier(RANDOM_STRING);
    report.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner(RANDOM_STRING);
    return createSingleProcessReport(report);
  }

  private String createAndStoreDefaultDecisionReportDefinition(DecisionReportDataDto reportData) {
    String id = addEmptyDecisionReportToOptimize();
    SingleDecisionReportDefinitionDto report = new SingleDecisionReportDefinitionDto();
    report.setData(reportData);
    report.setId(RANDOM_STRING);
    report.setLastModifier(RANDOM_STRING);
    report.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner(RANDOM_STRING);
    updateSingleDecisionReport(id, report);
    return id;
  }

  private ConflictResponseDto deleteReportFailWithConflict(String reportId, Boolean force) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .execute(ConflictResponseDto.class, 409);

  }

  private String createSingleProcessReport(SingleProcessReportDefinitionDto updatedReport) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequestWithDefinition(updatedReport)
      .execute(IdDto.class, 200)
      .getId();
  }

  private void updateSingleDecisionReport(String id, SingleDecisionReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleDecisionReportRequest(id, updatedReport)
      .execute();

    assertThat(response.getStatus(), is(204));
  }

  private ConflictResponseDto updateReportFailWithConflict(String id,
                                                           SingleProcessReportDefinitionDto updatedReport,
                                                           Boolean force) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport, force)
      .execute(ConflictResponseDto.class, 409);
  }

  private ConflictResponseDto updateReportFailWithConflict(String id,
                                                           SingleDecisionReportDefinitionDto updatedReport,
                                                           Boolean force) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleDecisionReportRequest(id, updatedReport, force)
      .execute(ConflictResponseDto.class, 409);
  }

  private String addEmptyProcessReportToOptimize() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private String addEmptyDecisionReportToOptimize() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private List<ReportDefinitionDto> getAllReports() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllReportsRequest()
      .executeAndReturnList(ReportDefinitionDto.class, 200);
  }

  public static class ForceParameterProvider {
    public static Object[] provideForceParameterAsBoolean() {
      return new Object[]{
        new Object[]{null},
        new Object[]{false},
      };
    }
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
