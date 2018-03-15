package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
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

  private static final String BEARER = "Bearer ";
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
      embeddedOptimizeRule.target("report")
        .request()
        .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewReport() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    // when
    Response response =
      embeddedOptimizeRule.target("report")
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
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
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = addEmptyReportToOptimize(token);

    // when
    ReportDefinitionDto reportDefinitionDto = constructReportWithFakePD();
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .put(Entity.json(reportDefinitionDto));

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private ReportDefinitionDto constructReportWithFakePD() {
    ReportDefinitionDto reportDefinitionDto = new ReportDefinitionDto();
    ReportDataDto data = new ReportDataDto();
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
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = addEmptyReportToOptimize(token);

    // when
    List<ReportDefinitionDto> reports = getAllReports(token);

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
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = addEmptyReportToOptimize(token);

    // when
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    ReportDefinitionDto report =
      response.readEntity(ReportDefinitionDto.class);
    assertThat(report, is(notNullValue()));
    assertThat(report.getId(), is(id));
  }

  @Test
  public void getReportForNonExistingIdThrowsError() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    // when
    Response response =
      embeddedOptimizeRule.target("report/FooId")
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(500));
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
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = addEmptyReportToOptimize(token);

    // when
    Response response =
        embeddedOptimizeRule.target("report/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .delete();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getAllReports(token).size(), is(0));
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
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = createAndStoreDefaultReportDefinition(
      ReportDataHelper.createReportDataViewRawAsTable(RANDOM_KEY, RANDOM_VERSION)
    );

    // then
    Response response = embeddedOptimizeRule.target("report/" + id + "/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void evaluateInvalidReportById() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = createAndStoreDefaultReportDefinition(
      ReportDataHelper.createCountFlowNodeFrequencyGroupByFlowNoneNumber(RANDOM_KEY, RANDOM_VERSION)
    );

    // then
    Response response = embeddedOptimizeRule.target("report/" + id + "/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(500));
    String errorMessage = response.readEntity(String.class);
    assertThat(errorMessage.contains("reportDefinition"), is(true));
    assertThat(errorMessage.contains("name"), is(true));
    assertThat(errorMessage.contains("id"), is(true));
    assertThat(errorMessage.contains("data"), is(true));
  }

  @Test
  public void evaluateReportWithoutViewById() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    ReportDataDto countFlowNodeFrequencyGroupByFlowNoneNumber = ReportDataHelper.createCountFlowNodeFrequencyGroupByFlowNoneNumber(RANDOM_KEY, RANDOM_VERSION);
    countFlowNodeFrequencyGroupByFlowNoneNumber.setView(null);
    String id = createAndStoreDefaultReportDefinition(
      countFlowNodeFrequencyGroupByFlowNoneNumber
    );

    // then
    Response response = embeddedOptimizeRule.target("report/" + id + "/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(500));
    String errorMessage = response.readEntity(String.class);
    assertThat(errorMessage.contains("reportDefinition"), is(true));
    assertThat(errorMessage.contains("name"), is(true));
    assertThat(errorMessage.contains("id"), is(true));
    assertThat(errorMessage.contains("data"), is(true));
  }

  @Test
  public void evaluateUnsavedReportWithoutAuthorization() {
    // when
    Response response =
        embeddedOptimizeRule.target("report/evaluate")
            .request()
            .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void evaluateUnsavedReport() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    ReportDataDto reportDataDto = ReportDataHelper.createReportDataViewRawAsTable(RANDOM_KEY, RANDOM_VERSION);

    // then
    Response response = embeddedOptimizeRule.target("report/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .post(Entity.json(reportDataDto));

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
  }

  private String createAndStoreDefaultReportDefinition(ReportDataDto reportDataViewRawAsTable) {
    String id = createNewReportHelper();
    ReportDataDto reportData = reportDataViewRawAsTable;
    ReportDefinitionDto report = new ReportDefinitionDto();
    report.setData(reportData);
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
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("report")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }

  private String addEmptyReportToOptimize(String token) {
    Response response =
      embeddedOptimizeRule.target("report")
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(""));

    return response.readEntity(IdDto.class).getId();
  }

  private List<ReportDefinitionDto> getAllReports(String token) {
    Response response =
      embeddedOptimizeRule.target("report")
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<ReportDefinitionDto>>() {
    });
  }
}
