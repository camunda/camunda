package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
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
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class DashboardRestServiceIT {

  public static final String BEARER = "Bearer ";
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void createNewDashboardWithoutAuthentication() throws IOException {
    // when
    Response response =
      embeddedOptimizeRule.target("dashboard")
        .request()
        .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewDashboard() throws IOException {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    // when
    Response response =
      embeddedOptimizeRule.target("dashboard")
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
  public void updateDashboardWithoutAuthentication() throws IOException {
    // when
    Response response =
      embeddedOptimizeRule.target("dashboard/1")
        .request()
        .put(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateDashboard() throws IOException {

    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = addEmptyDashboardToOptimize(token);

    // when
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    Response response =
      embeddedOptimizeRule.target("dashboard/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .put(Entity.json(dashboardDefinitionDto));

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void getStoredDashboardsWithoutAuthentication() throws IOException {
    // when
    Response response =
      embeddedOptimizeRule.target("dashboard")
        .request()
        .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getStoredDashboards() throws IOException {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = addEmptyDashboardToOptimize(token);

    // when
    List<DashboardDefinitionDto> dashboards = getAllDashboards(token);

    // then
    assertThat(dashboards.size(), is(1));
    assertThat(dashboards.get(0).getId(), is(id));
  }

  @Test
  public void getDashboardWithoutAuthentication() throws IOException {
    // when
    Response response =
      embeddedOptimizeRule.target("dashboard/asdf")
        .request()
        .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getDashboard() throws IOException {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = addEmptyDashboardToOptimize(token);

    // when
    Response response =
      embeddedOptimizeRule.target("dashboard/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    DashboardDefinitionDto dashboard =
      response.readEntity(DashboardDefinitionDto.class);
    assertThat(dashboard, is(notNullValue()));
    assertThat(dashboard.getId(), is(id));
  }

  @Test
  public void getDashboardForNonExistingIdThrowsError() throws IOException {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    // when
    Response response =
      embeddedOptimizeRule.target("dashboard/FooId")
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(500));
    assertThat(response.readEntity(String.class).contains("Dashboard does not exist!"), is(true));
  }

  @Test
  public void deleteDashboardWithoutAuthentication() throws IOException {
    // when
    Response response =
        embeddedOptimizeRule.target("dashboard/1124")
            .request()
            .delete();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNewDashboard() throws IOException {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = addEmptyDashboardToOptimize(token);

    // when
    Response response =
        embeddedOptimizeRule.target("dashboard/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .delete();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getAllDashboards(token).size(), is(0));
  }

  private String addEmptyDashboardToOptimize(String token) {
    Response response =
      embeddedOptimizeRule.target("dashboard")
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(""));

    return response.readEntity(IdDto.class).getId();
  }

  private List<DashboardDefinitionDto> getAllDashboards(String token) {
    Response response =
      embeddedOptimizeRule.target("dashboard")
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<DashboardDefinitionDto>>() {
    });
  }
}
