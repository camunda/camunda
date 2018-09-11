package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.service.exceptions.ReportEvaluationException;
import org.camunda.optimize.service.sharing.AbstractSharingIT;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


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
    Response response =
      embeddedOptimizeRule.target("report/single")
        .request()
        .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewReport() {
    // when
    Response response =
      embeddedOptimizeRule.target("report/single")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(""));

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    IdDto idDto =
      response.readEntity(IdDto.class);
    assertThat(idDto, is(notNullValue()));
  }

  @Test
  public void createNewCombinedReportWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target("report/combined")
        .request()
        .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createCombinedReport() {
    // when
    Response response =
      embeddedOptimizeRule.target("report/combined")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(""));

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    IdDto idDto =
      response.readEntity(IdDto.class);
    assertThat(idDto, is(notNullValue()));
  }

  @Test
  public void updateReportWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target("report/1")
        .request()
        .put(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateReport() {

    //given
    String id = addEmptyReportToOptimize();

    // when
    ReportDefinitionDto reportDefinitionDto = constructReportWithFakePD();
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.json(reportDefinitionDto));

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
    Response response =
      embeddedOptimizeRule.target("report")
        .request()
        .get();

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
    Response response =
      embeddedOptimizeRule.target("report/asdf")
        .request()
        .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getReport() {
    //given
    String id = addEmptyReportToOptimize();

    // when
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    ReportDefinitionDto report =
      response.readEntity(ReportDefinitionDto.class);
    assertThat(report, is(notNullValue()));
    assertThat(report.getId(), is(id));
  }

  @Test
  public void getReportForNonExistingIdThrowsNotFoundError() {
    // when
    Response response =
      embeddedOptimizeRule.target("report/FooId")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(404));
    assertThat(response.readEntity(String.class).contains("Report does not exist."), is(true));
  }

  @Test
  public void deleteReportWithoutAuthentication() {
    // when
    Response response =
        embeddedOptimizeRule.target("report/1124")
            .request()
            .delete();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNewReport() {
    //given
    String id = addEmptyReportToOptimize();

    // when
    Response response =
        embeddedOptimizeRule.target("report/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .delete();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getAllReports().size(), is(0));
  }
  
  @Test
  public void evaluateReportByIdWithoutAuthorization() {
   // when
    Response response = embeddedOptimizeRule.target("report/123/evaluate")
      .request()
      .get();

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
    Response response = embeddedOptimizeRule.target("report/" + id + "/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .get();

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
    Response response = embeddedOptimizeRule.target("report/" + id + "/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .get();

    // then
    assertThat(response.getStatus(), is(500));
    AbstractSharingIT.assertErrorFields(response.readEntity(ReportEvaluationException.class));
  }


  @Test
  public void evaluateReportWithoutViewById() {
    //given
    SingleReportDataDto countFlowNodeFrequencyGroupByFlowNoneNumber = ReportDataHelper.createCountFlowNodeFrequencyGroupByFlowNodeNumber(RANDOM_KEY, RANDOM_VERSION);
    countFlowNodeFrequencyGroupByFlowNoneNumber.setView(null);
    String id = createAndStoreDefaultReportDefinition(
      countFlowNodeFrequencyGroupByFlowNoneNumber
    );

    // then
    Response response = embeddedOptimizeRule.target("report/" + id + "/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .get();

    // then
    assertThat(response.getStatus(), is(500));
    AbstractSharingIT.assertErrorFields(response.readEntity(ReportEvaluationException.class));
  }

  @Test
  public void evaluateUnsavedReportWithoutAuthorization() {
    // when
    Response response =
        embeddedOptimizeRule.target("report/evaluate/single")
            .request()
            .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void evaluateUnsavedReport() {
    //given
    SingleReportDataDto reportDataDto = ReportDataHelper.createReportDataViewRawAsTable(RANDOM_KEY, RANDOM_VERSION);

    // then
    Response response = embeddedOptimizeRule.target("report/evaluate/single")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .post(Entity.json(reportDataDto));

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void evaluateUnsavedCombinedReportWithoutAuthorization() {
    // when
    Response response =
        embeddedOptimizeRule.target("report/evaluate/combined")
            .request()
            .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void evaluateCombinedUnsavedReport() {
    // then
    CombinedReportDataDto combinedReport = ReportDataHelper.createCombinedReport();
    Response response = embeddedOptimizeRule.target("report/evaluate/combined")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .post(Entity.json(combinedReport));

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void nullDataInCombinedReportThrowsReportEvaluationException() {
    // given
    Response response =
      embeddedOptimizeRule.target("report/combined")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));
    String reportId = response.readEntity(IdDto.class).getId();

    // then
    response = embeddedOptimizeRule.target("report/" + reportId + "/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .get();

    // then
    assertThat(response.getStatus(), is(500));
    ReportEvaluationException errorMessage = response.readEntity(ReportEvaluationException.class);
    assertThat(errorMessage.getReportDefinition(), is(notNullValue()));
    assertThat(errorMessage.getReportDefinition().getName(), is(notNullValue()));
    assertThat(errorMessage.getReportDefinition().getId(), is(notNullValue()));
  }

  @Test
  public void nullReportIdsThrowsReportEvaluationException() {
    // then
    CombinedReportDataDto combinedReport = ReportDataHelper.createCombinedReport();
    combinedReport.setReportIds(null);
    Response response = embeddedOptimizeRule.target("report/evaluate/combined")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .post(Entity.json(combinedReport));

    // then
    assertThat(response.getStatus(), is(500));
    ReportEvaluationException errorMessage = response.readEntity(ReportEvaluationException.class);
    assertThat(errorMessage.getReportDefinition(), is(notNullValue()));
    assertThat(errorMessage.getReportDefinition().getData(), is(notNullValue()));
  }

  private String createNewCombinedReport() {
    Response response =
      embeddedOptimizeRule.target("report/combined")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    String reportId = response.readEntity(IdDto.class).getId();
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    CombinedReportDataDto dataDto = new CombinedReportDataDto();
    report.setData(ReportDataHelper.createCombinedReport());
    updateReport(reportId, report);
    return reportId;
  }

  private String createAndStoreDefaultReportDefinition(SingleReportDataDto reportDataViewRawAsTable) {
    String id = createNewReportHelper();
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

  private String createNewReportHelper() {
    Response response =
      embeddedOptimizeRule.target("report/single")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }

  private String addEmptyReportToOptimize() {
    Response response =
      embeddedOptimizeRule.target("report/single")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(""));

    return response.readEntity(IdDto.class).getId();
  }

  private List<ReportDefinitionDto> getAllReports() {
    Response response =
      embeddedOptimizeRule.target("report")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<ReportDefinitionDto>>() {
    });
  }
}
