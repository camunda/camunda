package org.camunda.optimize.rest;

import junitparams.JUnitParamsRunner;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.service.exceptions.ReportEvaluationException;
import org.camunda.optimize.service.sharing.AbstractSharingIT;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.util.ReportDataBuilderHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


@RunWith(JUnitParamsRunner.class)
public class ReportRestServiceIT {

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

    // then
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateNonExistingReport() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateReportRequest("nonExistingId", constructReportWithFakePD())
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(404));
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

  @Test
  public void deleteNonExistingReport() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteReportRequest("nonExistingId")
      .execute();

    // then
    assertThat(response.getStatus(), is(404));
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
      ReportDataBuilderHelper.createReportDataViewRawAsTable(RANDOM_KEY, RANDOM_VERSION)
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
      ReportDataBuilderHelper.createCountFlowNodeFrequencyGroupByFlowNodeNumber(RANDOM_KEY, RANDOM_VERSION)
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
    SingleReportDataDto reportDataDto = ReportDataBuilderHelper.createReportDataViewRawAsTable(
      RANDOM_KEY,
      RANDOM_VERSION
    );

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
    CombinedReportDataDto combinedReport = ReportDataBuilderHelper.createCombinedReport();
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
    CombinedReportDataDto combinedReport = ReportDataBuilderHelper.createCombinedReport();
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
      ReportDataBuilderHelper.createCountFlowNodeFrequencyGroupByFlowNodeNumber(RANDOM_KEY, RANDOM_VERSION);
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

  private ReportDefinitionDto getReport(String id) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetReportRequest(id)
      .execute(ReportDefinitionDto.class, 200);
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

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateReportRequest(id, updatedReport)
      .execute();

    assertThat(response.getStatus(), is(204));
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
