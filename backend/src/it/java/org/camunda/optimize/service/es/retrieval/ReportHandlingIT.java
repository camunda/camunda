package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.dto.optimize.query.report.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.elasticsearch.action.get.GetResponse;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class ReportHandlingIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void reportIsWrittenToElasticsearch() {
    // given
    String id = createNewReport();

    // then
    GetResponse response =
      elasticSearchRule.getClient()
        .prepareGet(elasticSearchRule.getOptimizeIndex(),
          elasticSearchRule.getReportType(),
          id
        )
        .get();

    assertThat(response.isExists(), is(true));
  }

  @Test
  public void writeAndThenReadGivesTheSameResult() {
    // given
    String id = createNewReport();

    // when
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports, is(notNullValue()));
    assertThat(reports.size(), is(1));
    assertThat(reports.get(0).getId(), is(id));
  }

  @Test
  public void createAndGetSeveralReports() {
    // given
    String id = createNewReport();
    String id2 = createNewReport();
    Set<String> ids = new HashSet<>();
    ids.add(id);
    ids.add(id2);

    // when
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports, is(notNullValue()));
    assertThat(reports.size(), is(2));
    String reportId1 = reports.get(0).getId();
    String reportId2 = reports.get(1).getId();
    assertThat(ids.contains(reportId1), is(true));
    ids.remove(reportId1);
    assertThat(ids.contains(reportId2), is(true));
  }

  @Test
  public void noReportAvailableReturnsEmptyList() {

    // when
    List<ReportDefinitionDto> reports = getAllReports();

    // then
    assertThat(reports, is(notNullValue()));
    assertThat(reports.isEmpty(), is(true));
  }

  private String createNewReport() {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("report")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .post(Entity.json(""));
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    return response.readEntity(IdDto.class).getId();
  }


  private List<ReportDefinitionDto> getAllReports() {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("report")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<ReportDefinitionDto>>() {
    });
  }
}
