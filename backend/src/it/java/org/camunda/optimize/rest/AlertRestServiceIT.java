package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

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

public class AlertRestServiceIT extends AbstractAlertIT{

  private static final String ALERT = "alert";
  private static final String TEST = "test";
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
  public void cantCreateWithoutReport() {
    //given

    // when
    AlertCreationDto creationDto = new AlertCreationDto();
    Response response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(creationDto));

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void cantUpdateWithoutReport() throws Exception {
    //given
    AlertCreationDto creationDto = setupBasicAlert();
    String id = addAlertToOptimize(creationDto);
    creationDto.setReportId(TEST);

    // when
    Response response =
      embeddedOptimizeRule.target("alert/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.json(creationDto));

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void createNewAlert() throws Exception {
    //given
    AlertCreationDto creationDto = setupBasicAlert();

    // when
    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .post(Entity.json(creationDto));

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    String id =
        response.readEntity(String.class);
    assertThat(id, is(notNullValue()));
  }

  @Test
  public void createNewAlertAllowsMaxInt() throws Exception {
    //given
    AlertCreationDto creationDto = setupBasicAlert();
    creationDto.setThreshold(Integer.MAX_VALUE + 1);

    // when
    Response response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(creationDto));

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    String id =
      response.readEntity(String.class);
    assertThat(id, is(notNullValue()));
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
  public void updateAlert() throws Exception {

    //given
    AlertCreationDto creationDto = setupBasicAlert();
    String id = addAlertToOptimize(creationDto);
    creationDto.setEmail("new@camunda.com");


    // when
    Response response =
        embeddedOptimizeRule.target("alert/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .put(Entity.json(creationDto));

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
  public void getStoredAlerts() throws Exception {
    //given
    AlertCreationDto creationDto = setupBasicAlert();
    String id = addAlertToOptimize(creationDto);

    // when
    List<AlertDefinitionDto> allAlerts = getAllAlerts();

    // then
    assertThat(allAlerts.size(), is(1));
    assertThat(allAlerts.get(0).getId(), is(id));
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
  public void deleteNewAlert() throws Exception {
    //given
    AlertCreationDto creationDto = setupBasicAlert();
    String id = addAlertToOptimize(creationDto);

    // when
    Response response =
        embeddedOptimizeRule.target("alert/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .delete();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getAllAlerts().size(), is(0));
  }

  @Test
  public void emailNotificationIsEnabledCheckWithoutAuthentication() {
    // when
    Response response =
        embeddedOptimizeRule.target( "alert/email/isEnabled")
            .request()
            .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void emailNotificationIsEnabledCheckWithAuthentication() {
    // when
    Response response =
        embeddedOptimizeRule.target("alert/email/isEnabled")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    // then the status code is authorized
    assertThat(response.getStatus(), is(200));
  }

  private String addAlertToOptimize(AlertCreationDto creationDto) {
    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .post(Entity.json(creationDto));

    String id = response.readEntity(IdDto.class).getId();
    return id;
  }

  private List<AlertDefinitionDto> getAllAlerts() {
    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<AlertDefinitionDto>>() {
    });
  }
}
