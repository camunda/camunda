package org.camunda.optimize.rest;

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
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;
import org.camunda.optimize.service.exceptions.ReportEvaluationException;
import org.camunda.optimize.service.sharing.AbstractSharingIT;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;


@RunWith(JUnitParamsRunner.class)
public class ReportRestServiceIT {

  private static Object[] nullAndFalseParameter() {
    return new Object[]{new Object[]{Optional.empty(), Optional.of(false)}};
  }

  private static final String RANDOM_KEY = "someRandomKey";
  private static final String RANDOM_VERSION = "someRandomVersion";
  private static final String RANDOM_STRING = "something";
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void createNewReportWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleReportRequest()
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewReport() {
    // when
    IdDto idDto = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleReportRequest()
      .execute(IdDto.class, 200);
    // then
    assertThat(idDto, is(notNullValue()));
  }

  @Test
  public void createNewReportWithESWatermarkReached() throws InterruptedException {
    //given
    elasticSearchRule.setDiskWatermarks("1", "2", "3");

    //when
    ErrorResponseDto response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateSingleReportRequest()
            .execute(ErrorResponseDto.class, 500);

    //revert settings
    elasticSearchRule.setDiskWatermarks("85", "90", "95");
    elasticSearchRule.disableReadOnlyForAllIndexes();

    //then
    assertThat(response.getErrorMessage().contains("Your Elasticsearch index is set to read-only mode"), is(true));
  }

  @Test
  public void createNewCombinedReportWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildCreateCombinedReportRequest()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createCombinedReport() {
    // when
    IdDto idDto = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest()
      .execute(IdDto.class, 200);
    // then
    assertThat(idDto, is(notNullValue()));
  }

  @Test
  public void updateReportWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildUpdateReportRequest("1", null)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateReport() {
    //given
    String id = addEmptyReportToOptimize();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateReportRequest(id, constructReportWithFakePD())
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private ReportDefinitionDto constructReportWithFakePD() {
    SingleReportDefinitionDto reportDefinitionDto = new SingleReportDefinitionDto();
    SingleReportDataDto data = new SingleReportDataDto();
    data.setProcessDefinitionVersion("FAKE");
    data.setProcessDefinitionKey("FAKE");
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }

  @Test
  @Parameters
  public void updateSingleReportFailsWithConflictIfUsedInCombinedReportAndNotCombinableAnymoreWhenForceSet(Boolean force) {
    // given
    String firstSingleReportId = createAndStoreDefaultReportDefinition(
      ReportDataHelper.createReportDataViewRawAsTable(RANDOM_KEY, RANDOM_VERSION)
    );
    String secondSingleReportId = createAndStoreDefaultReportDefinition(
      ReportDataHelper.createReportDataViewRawAsTable(RANDOM_KEY, RANDOM_VERSION)
    );
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String combinedReportId = createNewCombinedReport(firstSingleReportId, secondSingleReportId);
    String[] expectedReportIds = new String[]{firstSingleReportId, secondSingleReportId, combinedReportId};
    String[] expectedConflictedItemIds = new String[]{combinedReportId};

    // when
    final SingleReportDefinitionDto firstSingleReport = (SingleReportDefinitionDto) getReport(firstSingleReportId);
    final SingleReportDefinitionDto reportUpdate = new SingleReportDefinitionDto();
    reportUpdate.setData(ReportDataHelper.createAverageProcessInstanceDurationGroupByStartDateReport(
      firstSingleReport.getData().getProcessDefinitionKey(),
      firstSingleReport.getData().getProcessDefinitionVersion(),
      ReportConstants.DATE_UNIT_DAY
    ));
    ConflictResponseDto conflictResponseDto = updateReportFailWithConflict(firstSingleReportId, reportUpdate, force);

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.COMBINED_REPORT, expectedConflictedItemIds);
    checkReportsStillExist(expectedReportIds);
    checkCombinedReportContainsSingleReports(combinedReportId, firstSingleReportId, secondSingleReportId);
  }

  private Boolean[] parametersForUpdateSingleReportFailsWithConflictIfUsedInCombinedReportAndNotCombinableAnymoreWhenForceSet() {
    return new Boolean[]{null, false};
  }

  @Test
  @Parameters
  public void updateSingleReportFailsWithConflictIfUsedInAlertAndSuitableforAklertAnymoreWhenForceSet(Boolean force) {
    // given
    String reportId = createAndStoreDefaultReportDefinition(
      ReportDataHelper.createPiFrequencyCountGroupedByNoneAsNumber(RANDOM_KEY, RANDOM_VERSION)
    );
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String alertForReport = createNewAlertForReport(reportId);
    String[] expectedReportIds = new String[]{reportId};
    String[] expectedConflictedItemIds = new String[]{alertForReport};

    // when
    final SingleReportDefinitionDto singleReport = (SingleReportDefinitionDto) getReport(reportId);
    final SingleReportDefinitionDto reportUpdate = new SingleReportDefinitionDto();
    reportUpdate.setData(ReportDataHelper.createAverageProcessInstanceDurationGroupByStartDateReport(
      singleReport.getData().getProcessDefinitionKey(),
      singleReport.getData().getProcessDefinitionVersion(),
      ReportConstants.DATE_UNIT_DAY
    ));
    ConflictResponseDto conflictResponseDto = updateReportFailWithConflict(reportId, reportUpdate, force);

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.ALERT, expectedConflictedItemIds);
    checkReportsStillExist(expectedReportIds);
    checkAlertsStillExist(expectedConflictedItemIds);
  }

  private Boolean[] parametersForUpdateSingleReportFailsWithConflictIfUsedInAlertAndSuitableforAklertAnymoreWhenForceSet() {
    return new Boolean[]{null, false};
  }

  @Test
  public void getStoredReportsWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetAllReportsRequest()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getStoredReports() {
    //given
    String id = addEmptyReportToOptimize();

    // when
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports.size(), is(1));
    assertThat(reports.get(0).getId(), is(id));
  }

  @Test
  public void getReportWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetReportRequest("asdf")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getReport() {
    //given
    String id = addEmptyReportToOptimize();

    // when
    ReportDefinitionDto report = getReport(id);

    // then the status code is okay
    assertThat(report, is(notNullValue()));
    assertThat(report.getId(), is(id));
  }

  @Test
  public void getReportForNonExistingIdThrowsNotFoundError() {
    // when
    String response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetReportRequest("fooId")
      .execute(String.class, 404);

    // then the status code is okay
    assertThat(response.contains("Report does not exist."), is(true));
  }

  @Test
  public void deleteReportWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildDeleteReportRequest("1124")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNewReport() {
    //given
    String id = addEmptyReportToOptimize();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteReportRequest(id)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getAllReports().size(), is(0));
  }

  private static CombinedReportDataDto createCombinedReport(String... reportIds) {
    CombinedReportDataDto combinedReportDataDto = new CombinedReportDataDto();
    combinedReportDataDto.setReportIds(Arrays.asList(reportIds));
    combinedReportDataDto.setConfiguration("aRandomConfiguration");
    return combinedReportDataDto;
  }

  @Test
  public void getSingleReportDeleteConflictsIfUsedByCombinedReport() {
    // given
    String firstSingleReportId = addEmptyReportToOptimize();
    String secondSingleReportId = addEmptyReportToOptimize();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String firstCombinedReportId = createNewCombinedReport(firstSingleReportId, secondSingleReportId);
    String secondCombinedReportId = createNewCombinedReport(firstSingleReportId, secondSingleReportId);
    String[] expectedConflictedItemIds = {firstCombinedReportId, secondCombinedReportId};

    // when
    ConflictResponseDto conflictResponseDto = getReportDeleteConflicts(firstSingleReportId);

    // then
    checkConflictedItems(conflictResponseDto, ConflictedItemType.COMBINED_REPORT, expectedConflictedItemIds);
  }

  @Test
  @Parameters
  public void deleteSingleReportsFailsWithConflictIfUsedByCombinedReportWhenForceSet(Boolean force) {
    // given
    String firstSingleReportId = addEmptyReportToOptimize();
    String secondSingleReportId = addEmptyReportToOptimize();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String firstCombinedReportId = createNewCombinedReport(firstSingleReportId, secondSingleReportId);
    String secondCombinedReportId = createNewCombinedReport(firstSingleReportId, secondSingleReportId);
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

  private Boolean[] parametersForDeleteSingleReportsFailsWithConflictIfUsedByCombinedReportWhenForceSet() {
    return new Boolean[]{null, false};
  }

  @Test
  @Parameters
  public void deleteSingleReportsFailsWithConflictIfUsedByAlertWhenForceSet(Boolean force) {
    // given
    String reportId = addEmptyReportToOptimize();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
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

  private Boolean[] parametersForDeleteSingleReportsFailsWithConflictIfUsedByAlertWhenForceSet() {
    return new Boolean[]{null, false};
  }

  @Test
  @Parameters
  public void deleteSingleReportsFailsWithConflictIfUsedByDashboardWhenForceSet(Boolean force) {
    // given
    String reportId = addEmptyReportToOptimize();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
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

  private Boolean[] parametersForDeleteSingleReportsFailsWithConflictIfUsedByDashboardWhenForceSet() {
    return new Boolean[]{null, false};
  }

  @Test
  public void evaluateReportByIdWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildEvaluateSavedReportRequest("123")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void evaluateReportById() {
    //given
    String id = createAndStoreDefaultReportDefinition(
      ReportDataHelper.createReportDataViewRawAsTable(RANDOM_KEY, RANDOM_VERSION)
    );

    // then
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void evaluateInvalidReportById() {
    //given
    String id = createAndStoreDefaultReportDefinition(
      ReportDataHelper.createCountFlowNodeFrequencyGroupByFlowNodeNumber(RANDOM_KEY, RANDOM_VERSION)
    );

    // then
    ReportEvaluationException response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      .execute(ReportEvaluationException.class, 500);

    // then
    AbstractSharingIT.assertErrorFields(response);
  }

  @Test
  public void evaluateUnsavedReportWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildEvaluateCombinedUnsavedReportRequest(null)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void evaluateUnsavedReport() {
    //given
    SingleReportDataDto reportDataDto = ReportDataHelper.createReportDataViewRawAsTable(RANDOM_KEY, RANDOM_VERSION);

    // then
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportDataDto)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void evaluateUnsavedCombinedReportWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildEvaluateCombinedUnsavedReportRequest(null)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void evaluateCombinedUnsavedReport() {
    // then
    CombinedReportDataDto combinedReport = ReportDataHelper.createCombinedReport();
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(combinedReport)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void nullDataInCombinedReportThrowsReportEvaluationException() {
    // given
    String reportId = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest()
      .execute(IdDto.class, 200)
      .getId();

    // then
    ReportEvaluationException errorMessage = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      .execute(ReportEvaluationException.class, 500);

    // then
    assertThat(errorMessage.getReportDefinition(), is(notNullValue()));
    assertThat(errorMessage.getReportDefinition().getName(), is(notNullValue()));
    assertThat(errorMessage.getReportDefinition().getId(), is(notNullValue()));
  }

  @Test
  public void nullReportIdsThrowsReportEvaluationException() {
    // then
    CombinedReportDataDto combinedReport = ReportDataHelper.createCombinedReport();
    combinedReport.setReportIds(null);

    ReportEvaluationException errorMessage = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(combinedReport)
      .execute(ReportEvaluationException.class, 500);

    // then
    assertThat(errorMessage.getReportDefinition(), is(notNullValue()));
    assertThat(errorMessage.getReportDefinition().getData(), is(notNullValue()));
  }

  @Test
  public void evaluateReportWithoutViewById() {
    //given
    SingleReportDataDto countFlowNodeFrequencyGroupByFlowNoneNumber =
      ReportDataHelper.createCountFlowNodeFrequencyGroupByFlowNodeNumber(RANDOM_KEY, RANDOM_VERSION);
    countFlowNodeFrequencyGroupByFlowNoneNumber.setView(null);
    String id = createAndStoreDefaultReportDefinition(
      countFlowNodeFrequencyGroupByFlowNoneNumber
    );

    // then
    ReportEvaluationException response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      .execute(ReportEvaluationException.class, 500);

    // then
    AbstractSharingIT.assertErrorFields(response);
  }

  private void checkDashboardsStillContainReport(String[] expectedConflictedItemIds, String reportId) {
    List<DashboardDefinitionDto> dashboards = getAllDashboards();

    assertThat(dashboards.size(), is(expectedConflictedItemIds.length));
    assertThat(
      dashboards.stream().map(DashboardDefinitionDto::getId).collect(Collectors.toSet()),
      containsInAnyOrder(expectedConflictedItemIds)
    );
    dashboards.forEach(dashboardDefinitionDto -> {
      assertThat(dashboardDefinitionDto.getReports().size(), is(1));
      assertThat(
        dashboardDefinitionDto.getReports().stream().anyMatch(
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

  private List<DashboardDefinitionDto> getAllDashboards() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllDashboardsRequest()
      .executeAndReturnList(DashboardDefinitionDto.class, 200);
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

  private String createNewCombinedReport(String... singleReportIds) {
    String reportId = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest()
      .execute(IdDto.class, 200)
      .getId();

    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReport(singleReportIds));
    updateReport(reportId, report);
    return reportId;
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

  private String createAndStoreDefaultReportDefinition(SingleReportDataDto reportDataViewRawAsTable) {
    String id = addEmptyReportToOptimize();
    SingleReportDefinitionDto report = new SingleReportDefinitionDto();
    report.setData(reportDataViewRawAsTable);
    report.setId(RANDOM_STRING);
    report.setLastModifier(RANDOM_STRING);
    report.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner(RANDOM_STRING);
    updateReport(id, report);
    return id;
  }

  private void deleteReport(String reportId, Boolean force) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private ConflictResponseDto deleteReportFailWithConflict(String reportId, Boolean force) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .execute(ConflictResponseDto.class, 409);

  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateReportRequest(id, updatedReport)
      .execute();

    assertThat(response.getStatus(), is(204));
  }


  private ConflictResponseDto updateReportFailWithConflict(String id,
                                                           ReportDefinitionDto updatedReport,
                                                           Boolean force) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateReportRequest(id, updatedReport, force)
      .execute(ConflictResponseDto.class, 409);
  }

  private String addEmptyReportToOptimize() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private List<ReportDefinitionDto> getAllReports() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllReportsRequest()
      .executeAndReturnList(ReportDefinitionDto.class, 200);
  }
}
