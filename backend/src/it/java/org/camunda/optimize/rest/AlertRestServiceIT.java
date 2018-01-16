package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
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

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class AlertRestServiceIT {

  public static final String BEARER = "Bearer ";
  public static final String ALERT = "alert";
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void createNewAlertWithoutAuthentication() {
    // when
    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewAlert() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    // when
    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(new AlertCreationDto()));

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    AlertDefinitionDto alertDefinitionDto =
        response.readEntity(AlertDefinitionDto.class);
    assertThat(alertDefinitionDto, is(notNullValue()));
    assertThat(alertDefinitionDto.getId(), is(notNullValue()));
    assertThat(alertDefinitionDto.getCreated(), is(notNullValue()));
    assertThat(alertDefinitionDto.getLastModified(), is(notNullValue()));
    assertThat(alertDefinitionDto.getLastModifier(), is(notNullValue()));
    assertThat(alertDefinitionDto.getOwner(), is(notNullValue()));
  }

  @Test
  public void updateAlertWithoutAuthentication() {
    // when
    Response response =
        embeddedOptimizeRule.target("alert/1")
            .request()
            .put(Entity.json(new AlertCreationDto()));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateAlert() {

    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = addEmptyAlertToOptimize(token);

    // when
    AlertCreationDto dashboardDefinitionDto = new AlertCreationDto();
    Response response =
        embeddedOptimizeRule.target("alert/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .put(Entity.json(dashboardDefinitionDto));

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void getStoredAlertsWithoutAuthentication() {
    // when
    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getStoredAlerts() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = addEmptyAlertToOptimize(token);

    // when
    List<AlertDefinitionDto> dashboards = getAllAlerts(token);

    // then
    assertThat(dashboards.size(), is(1));
    assertThat(dashboards.get(0).getId(), is(id));
  }

  @Test
  public void deleteAlertWithoutAuthentication() {
    // when
    Response response =
        embeddedOptimizeRule.target("alert/1124")
            .request()
            .delete();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNewAlert() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String id = addEmptyAlertToOptimize(token);

    // when
    Response response =
        embeddedOptimizeRule.target("alert/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .delete();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getAllAlerts(token).size(), is(0));
  }

  private String addEmptyAlertToOptimize(String token) {
    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(new AlertCreationDto()));

    return response.readEntity(AlertDefinitionDto.class).getId();
  }

  private List<AlertDefinitionDto> getAllAlerts(String token) {
    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<AlertDefinitionDto>>() {
    });
  }
}
