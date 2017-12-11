package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.elasticsearch.action.get.GetResponse;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class DashboardHandlingIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @After
  public void cleanUp() {
    LocalDateUtil.reset();
  }

  @Test
  public void dashboardIsWrittenToElasticsearch() {
    // given
    String id = createNewDashboard();

    // then
    GetResponse response =
      elasticSearchRule.getClient()
        .prepareGet(elasticSearchRule.getOptimizeIndex(elasticSearchRule.getDashboardType()),
          elasticSearchRule.getDashboardType(),
          id
        )
        .get();

    assertThat(response.isExists(), is(true));
  }

  @Test
  public void writeAndThenReadGivesTheSameResult() throws IOException {
    // given
    String id = createNewDashboard();

    // when
    List<DashboardDefinitionDto> dashboards = getAllDashboards();

    // then
    assertThat(dashboards, is(notNullValue()));
    assertThat(dashboards.size(), is(1));
    assertThat(dashboards.get(0).getId(), is(id));
  }

  @Test
  public void createAndGetSeveralDashboards() throws IOException {
    // given
    String id = createNewDashboard();
    String id2 = createNewDashboard();
    Set<String> ids = new HashSet<>();
    ids.add(id);
    ids.add(id2);

    // when
    List<DashboardDefinitionDto> dashboards = getAllDashboards();

    // then
    assertThat(dashboards, is(notNullValue()));
    assertThat(dashboards.size(), is(2));
    String dashboardId1 = dashboards.get(0).getId();
    String dashboardId2 = dashboards.get(1).getId();
    assertThat(ids.contains(dashboardId1), is(true));
    ids.remove(dashboardId1);
    assertThat(ids.contains(dashboardId2), is(true));
  }

  @Test
  public void noDashboardAvailableReturnsEmptyList() throws IOException {

    // when
    List<DashboardDefinitionDto> dashboards = getAllDashboards();

    // then
    assertThat(dashboards, is(notNullValue()));
    assertThat(dashboards.isEmpty(), is(true));
  }

  @Test
  public void updateDashboard() throws Exception {
    // given
    String id = createNewDashboard();
    ReportLocationDto reportLocationDto = new ReportLocationDto();
    reportLocationDto.setId("report-123");
    DashboardDefinitionDto dashboard = new DashboardDefinitionDto();
    dashboard.setReports(Collections.singletonList(reportLocationDto));
    dashboard.setId("shouldNotBeUpdated");
    dashboard.setLastModifier("shouldNotBeUpdatedManually");
    dashboard.setName("MyDashboard");
    OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    dashboard.setCreated(shouldBeIgnoredDate);
    dashboard.setLastModified(shouldBeIgnoredDate);
    dashboard.setOwner("NewOwner");

    // when
    updateDashboard(id, dashboard);
    List<DashboardDefinitionDto> dashboards = getAllDashboards();

    // then
    assertThat(dashboards.size(), is(1));
    DashboardDefinitionDto newDashboard = dashboards.get(0);
    assertThat(newDashboard.getReports().size(), is(1));
    ReportLocationDto retrievedLocation = newDashboard.getReports().get(0);
    assertThat(retrievedLocation.getId(), is("report-123"));
    assertThat(newDashboard.getId(), is(id));
    assertThat(newDashboard.getCreated(), is(not(shouldBeIgnoredDate)));
    assertThat(newDashboard.getLastModified(), is(not(shouldBeIgnoredDate)));
    assertThat(newDashboard.getName(), is("MyDashboard"));
    assertThat(newDashboard.getOwner(), is("NewOwner"));
  }

  @Test
  public void doNotUpdateNullFieldsInDashboard() throws Exception {
    // given
    String id = createNewDashboard();
    DashboardDefinitionDto dashboard = new DashboardDefinitionDto();

    // when
    updateDashboard(id, dashboard);
    List<DashboardDefinitionDto> dashboards = getAllDashboards();

    // then
    assertThat(dashboards.size(), is(1));
    DashboardDefinitionDto newDashboard = dashboards.get(0);
    assertThat(newDashboard.getId(), is(id));
    assertThat(newDashboard.getCreated(), is(notNullValue()));
    assertThat(newDashboard.getLastModified(), is(notNullValue()));
    assertThat(newDashboard.getLastModifier(), is(notNullValue()));
    assertThat(newDashboard.getName(), is(notNullValue()));
    assertThat(newDashboard.getOwner(), is(notNullValue()));
  }

  @Test
  public void resultListIsSortedByLastModified() throws IOException {
    // given
    String id1 = createNewDashboard();
    shiftTimeByOneSecond();
    String id2 = createNewDashboard();
    shiftTimeByOneSecond();
    String id3 = createNewDashboard();
    shiftTimeByOneSecond();
    updateDashboard(id1, new DashboardDefinitionDto());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("orderBy", "lastModified");
    List<DashboardDefinitionDto> dashboards = getAllDashboardsWithQueryParam(queryParam);

    // then
    assertThat(dashboards.size(), is(3));
    assertThat(dashboards.get(0).getId(), is(id1));
    assertThat(dashboards.get(1).getId(), is(id3));
    assertThat(dashboards.get(2).getId(), is(id2));
  }

  @Test
  public void resultListIsReversed() throws Exception {
    // given
    String id1 = createNewDashboard();
    shiftTimeByOneSecond();
    String id2 = createNewDashboard();
    shiftTimeByOneSecond();
    String id3 = createNewDashboard();
    shiftTimeByOneSecond();
    updateDashboard(id1, new DashboardDefinitionDto());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("orderBy", "lastModified");
    queryParam.put("reverseOrder", true);
    List<DashboardDefinitionDto> dashboards = getAllDashboardsWithQueryParam(queryParam);

    // then
    assertThat(dashboards.size(), is(3));
    assertThat(dashboards.get(2).getId(), is(id1));
    assertThat(dashboards.get(1).getId(), is(id3));
    assertThat(dashboards.get(0).getId(), is(id2));
  }

  @Test
  public void resultListIsCutByAnOffset() throws Exception {
    // given
    String id1 = createNewDashboard();
    shiftTimeByOneSecond();
    String id2 = createNewDashboard();
    shiftTimeByOneSecond();
    String id3 = createNewDashboard();
    shiftTimeByOneSecond();
    updateDashboard(id1, new DashboardDefinitionDto());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("resultOffset", 1);
    queryParam.put("orderBy", "lastModified");
    List<DashboardDefinitionDto> dashboards = getAllDashboardsWithQueryParam(queryParam);

    // then
    assertThat(dashboards.size(), is(2));
    assertThat(dashboards.get(0).getId(), is(id3));
    assertThat(dashboards.get(1).getId(), is(id2));
  }

  @Test
  public void resultListIsCutByMaxResults() throws Exception {
    // given
    String id1 = createNewDashboard();
    shiftTimeByOneSecond();
    createNewDashboard();
    shiftTimeByOneSecond();
    String id3 = createNewDashboard();
    shiftTimeByOneSecond();
    updateDashboard(id1, new DashboardDefinitionDto());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("numResults", 2);
    queryParam.put("orderBy", "lastModified");
    List<DashboardDefinitionDto> dashboards = getAllDashboardsWithQueryParam(queryParam);

    // then
    assertThat(dashboards.size(), is(2));
    assertThat(dashboards.get(0).getId(), is(id1));
    assertThat(dashboards.get(1).getId(), is(id3));
  }

  @Test
  public void combineAllResultListQueryParameterRestrictions() throws Exception {
    // given
    String id1 = createNewDashboard();
    shiftTimeByOneSecond();
    createNewDashboard();
    shiftTimeByOneSecond();
    String id3 = createNewDashboard();
    shiftTimeByOneSecond();
    updateDashboard(id1, new DashboardDefinitionDto());

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("numResults", 1);
    queryParam.put("orderBy", "lastModified");
    queryParam.put("reverseOrder", true);
    queryParam.put("resultOffset", 1);
    List<DashboardDefinitionDto> dashboards = getAllDashboardsWithQueryParam(queryParam);

    // then
    assertThat(dashboards.size(), is(1));
    assertThat(dashboards.get(0).getId(), is(id3));
  }

  private void shiftTimeByOneSecond() {
    LocalDateUtil.setCurrentTime(LocalDateUtil.getCurrentDateTime().plusSeconds(1L));
  }

  private String createNewDashboard() {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("dashboard")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }

  private void updateDashboard(String id, DashboardDefinitionDto updatedDashboard) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("dashboard/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .put(Entity.json(updatedDashboard));
    assertThat(response.getStatus(), is(204));
  }


  private List<DashboardDefinitionDto> getAllDashboards() throws IOException {
    return getAllDashboardsWithQueryParam(new HashMap<>());
  }

  private List<DashboardDefinitionDto> getAllDashboardsWithQueryParam(Map<String, Object> queryParams) throws IOException {
    String token = embeddedOptimizeRule.getAuthenticationToken();
      WebTarget webTarget = embeddedOptimizeRule.target("dashboard");
    for (Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
      webTarget = webTarget.queryParam(queryParam.getKey(), queryParam.getValue());
    }
    Response response =
      webTarget
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<DashboardDefinitionDto>>() {
    });
  }
}
