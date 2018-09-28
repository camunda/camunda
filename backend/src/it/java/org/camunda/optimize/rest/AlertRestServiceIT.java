package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;



public class AlertRestServiceIT extends AbstractAlertIT{

  private static final String TEST = "test";
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void createNewAlertWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildCreateAlertRequest(null)
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void cantCreateWithoutReport() {
    // when
    Response response = embeddedOptimizeRule
    .getRequestExecutor()
    .buildCreateAlertRequest(new AlertCreationDto())
    .execute();

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
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildUpdateAlertRequest(id, creationDto)
            .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void createNewAlert() throws Exception {
    //given
    AlertCreationDto creationDto = setupBasicAlert();

    // when
    String id = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateAlertRequest(creationDto)
            .execute(String.class, 200);

    // then the status code is okay
    assertThat(id, is(notNullValue()));
  }

  @Test
  public void createNewAlertAllowsMaxInt() throws Exception {
    //given
    AlertCreationDto creationDto = setupBasicAlert();
    creationDto.setThreshold(Integer.MAX_VALUE + 1);

    // when
    String id = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateAlertRequest(creationDto)
            .execute(String.class, 200);

    // then the status code is okay
    assertThat(id, is(notNullValue()));
  }

  @Test
  public void updateAlertWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildUpdateAlertRequest("1", new AlertCreationDto())
            .execute();

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
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildUpdateAlertRequest(id, creationDto)
            .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void getStoredAlertsWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildGetAllAlertsRequest()
            .execute();

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
  public void getAuthorizedAlertsOnly() throws Exception {
    //given
    engineRule.addUser("kermit", "kermit");
    engineRule.grantUserOptimizeAccess("kermit");
    AlertCreationDto alert1 = setupBasicAlert("processDefinition1");
    AlertCreationDto alert2 = setupBasicAlert("processDefinition2");
    addAlertToOptimize(alert1);
    addAlertToOptimize(alert2);
    grantSingleDefinitionAuthorizationsForUser("kermit", "processDefinition1");

    // when

    List<AlertDefinitionDto> allAlerts = embeddedOptimizeRule
            .getRequestExecutor()
            .withUserAuthentication("kermit", "kermit")
            .buildGetAllAlertsRequest()
            .executeAndReturnList(AlertDefinitionDto.class, 200);

    // then
    assertThat(allAlerts.size(), is(1));
  }

  @Test
  public void deleteAlertWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildDeleteAlertRequest("1124")
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNewAlert() throws Exception {
    //given
    AlertCreationDto creationDto = setupBasicAlert();
    String id = addAlertToOptimize(creationDto);

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildDeleteAlertRequest(id)
            .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getAllAlerts().size(), is(0));
  }

  @Test
  public void emailNotificationIsEnabledCheckWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildEmailNotificationIsEnabledRequest()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void emailNotificationIsEnabledCheckWithAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEmailNotificationIsEnabledRequest()
            .execute();

    // then the status code is authorized
    assertThat(response.getStatus(), is(200));
  }

  private String addAlertToOptimize(AlertCreationDto creationDto) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateAlertRequest(creationDto)
            .execute(IdDto.class, 200)
            .getId();
  }

  private List<AlertDefinitionDto> getAllAlerts() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetAllAlertsRequest()
            .executeAndReturnList(AlertDefinitionDto.class, 200);
  }
}
