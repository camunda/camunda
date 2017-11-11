package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_RAW_DATA_OPERATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class ReportRestServiceIT {

  public static final String BEARER = "Bearer ";
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void createNewReportWithoutAuthentication() throws IOException {
    // when
    Response response =
      embeddedOptimizeRule.target("report")
        .request()
        .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewReport() throws IOException {
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
  public void updateReportWithoutAuthentication() throws IOException {
    // when
    Response response =
      embeddedOptimizeRule.target("report/1")
        .request()
        .put(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateReport() throws IOException {

    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = addEmptyReportToOptimize(token);

    // when
    ReportDefinitionDto reportDefinitionDto = new ReportDefinitionDto();
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .put(Entity.json(reportDefinitionDto));

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void getStoredReportsWithoutAuthentication() throws IOException {
    // when
    Response response =
      embeddedOptimizeRule.target("report")
        .request()
        .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getStoredReports() throws IOException {
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
  public void getReportWithoutAuthentication() throws IOException {
    // when
    Response response =
      embeddedOptimizeRule.target("report/asdf")
        .request()
        .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getReport() throws IOException {
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
  public void getReportForNonExistingIdThrowsError() throws IOException {
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
    assertThat(response.readEntity(String.class).contains("Report does not exist!"), is(true));
  }

  @Test
  public void deleteReportWithoutAuthentication() throws IOException {
    // when
    Response response =
        embeddedOptimizeRule.target("report/1124")
            .request()
            .delete();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNewReport() throws IOException {
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
  public void evaluateReportByIdWithoutAuthorization() throws Exception {
   // when
    Response response = embeddedOptimizeRule.target("report/123/evaluate")
      .request()
      .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void evaluateReportById() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = createAndStoreDefaultReportDefinition("someRandomId");

    // then
    Response response = embeddedOptimizeRule.target("report/" + id + "/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void evaluateUnsavedReportWithoutAuthorization() throws Exception {
    // when
    Response response =
        embeddedOptimizeRule.target("report/evaluate")
            .request()
            .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void evaluateUnsavedReport() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    ReportDataDto reportDataDto = createDefaultReportData("someRandomId");

    // then
    Response response = embeddedOptimizeRule.target("report/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .post(Entity.json(reportDataDto));

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionId) {
    String id = createNewReportHelper();
    ReportDataDto reportData = createDefaultReportData(processDefinitionId);
    ReportDefinitionDto report = new ReportDefinitionDto();
    report.setData(reportData);
    report.setId("something");
    report.setLastModifier("something");
    report.setName("something");
    LocalDateTime someDate = LocalDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner("something");
    updateReport(id, report);
    return id;
  }

  private ReportDataDto createDefaultReportData(String processDefinitionId) {
    ReportDataDto reportData = new ReportDataDto();
    reportData.setProcessDefinitionId(processDefinitionId);
    reportData.setVisualization("table");
    reportData.setView(new ViewDto(VIEW_RAW_DATA_OPERATION));
    return reportData;
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
