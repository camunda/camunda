package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
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


public class DashboardRestServiceIT {

  public static final String BEARER = "Bearer ";
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void createNewDashboardWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target("dashboard")
        .request()
        .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewDashboard() {

    // when
    Response response =
      embeddedOptimizeRule.target("dashboard")
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
  public void updateDashboardWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target("dashboard/1")
        .request()
        .put(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateDashboard() {

    //given
    String id = addEmptyDashboardToOptimize();

    // when
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    Response response =
      embeddedOptimizeRule.target("dashboard/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.json(dashboardDefinitionDto));

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void getStoredDashboardsWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target("dashboard")
        .request()
        .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getStoredDashboards() {
    //given
    String id = addEmptyDashboardToOptimize();

    // when
    List<DashboardDefinitionDto> dashboards = getAllDashboards();

    // then
    assertThat(dashboards.size(), is(1));
    assertThat(dashboards.get(0).getId(), is(id));
  }

  @Test
  public void getDashboardWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target("dashboard/asdf")
        .request()
        .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getDashboard() {
    //given
    String id = addEmptyDashboardToOptimize();

    // when
    Response response =
      embeddedOptimizeRule.target("dashboard/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    DashboardDefinitionDto dashboard =
      response.readEntity(DashboardDefinitionDto.class);
    assertThat(dashboard, is(notNullValue()));
    assertThat(dashboard.getId(), is(id));
  }

  @Test
  public void getDashboardForNonExistingIdThrowsError() {
    // when
    Response response =
      embeddedOptimizeRule.target("dashboard/FooId")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(500));
    assertThat(response.readEntity(String.class).contains("Dashboard does not exist!"), is(true));
  }

  @Test
  public void deleteDashboardWithoutAuthentication() {
    // when
    Response response =
        embeddedOptimizeRule.target("dashboard/1124")
            .request()
            .delete();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNewDashboard() {
    //given
    String id = addEmptyDashboardToOptimize();

    // when
    Response response =
        embeddedOptimizeRule.target("dashboard/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .delete();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getAllDashboards().size(), is(0));
  }

  private String addEmptyDashboardToOptimize() {
    Response response =
      embeddedOptimizeRule.target("dashboard")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(""));

    return response.readEntity(IdDto.class).getId();
  }

  private List<DashboardDefinitionDto> getAllDashboards() {
    Response response =
      embeddedOptimizeRule.target("dashboard")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<DashboardDefinitionDto>>() {
    });
  }
}
