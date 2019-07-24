/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class DashboardRestServiceIT {

  public EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineIntegrationRule).around(embeddedOptimizeRule);

  @Test
  public void createNewDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildCreateDashboardRequest()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewDashboard() {
    // when
    IdDto idDto = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateDashboardRequest()
            .execute(IdDto.class, 200);

    // then the status code is okay
    assertThat(idDto, is(notNullValue()));
  }

  @Test
  public void updateDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildUpdateDashboardRequest("1", null)
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateNonExistingDashboard() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildUpdateDashboardRequest("nonExistingId", new DashboardDefinitionDto())
            .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void updateDashboard() {
    //given
    String id = addEmptyDashboardToOptimize();

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildUpdateDashboardRequest(id, new DashboardDefinitionDto())
            .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void getStoredDashboardsWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildGetAllDashboardsRequest()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getStoredDashboards() {
    OptimizeRequestExecutor requestExecutor = embeddedOptimizeRule
            .getRequestExecutor();

    //given
    String id = requestExecutor
            .buildCreateDashboardRequest()
            .execute(IdDto.class, 200)
            .getId();

    // when
    List<DashboardDefinitionDto> dashboards = getAllDashboards();

    // then
    assertThat(dashboards.size(), is(1));
    assertThat(dashboards.get(0).getId(), is(id));
  }
  @Test
  public void getDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildGetDashboardRequest("asdf")
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getDashboard() {
    //given
    String id = addEmptyDashboardToOptimize();

    // when
    DashboardDefinitionDto dashboard = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetDashboardRequest(id)
            .execute(DashboardDefinitionDto.class, 200);

    // then
    assertThat(dashboard, is(notNullValue()));
    assertThat(dashboard.getId(), is(id));
  }

  @Test
  public void getDashboardForNonExistingIdThrowsError() {
    // when
    String response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetDashboardRequest("fooid")
            .execute(String.class, 404);

    // then the status code is okay
    assertThat(response.contains("Dashboard does not exist!"), is(true));
  }

  @Test
  public void deleteDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildDeleteDashboardRequest("1124")
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNewDashboard() {
    //given
    String id = addEmptyDashboardToOptimize();

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildDeleteDashboardRequest(id)
            .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getAllDashboards().size(), is(0));
  }

  @Test
  public void deleteNonExistingDashboard() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildDeleteDashboardRequest("nonExistingId")
            .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  private String addEmptyDashboardToOptimize() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateDashboardRequest()
            .execute(IdDto.class, 200)
            .getId();
  }

  private List<DashboardDefinitionDto> getAllDashboards() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetAllDashboardsRequest()
            .executeAndReturnList(DashboardDefinitionDto.class, 200);
  }
}
