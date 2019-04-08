/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.WebappsEndpointDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

public class CamundaRestServiceIT {

  public static final String DEFAULT_ENGINE = "1";
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule);

  private String defaultWebappsEndpoint;

  @Before
  public void init() {
    defaultWebappsEndpoint = embeddedOptimizeRule
      .getConfigurationService()
      .getConfiguredEngines()
      .get(DEFAULT_ENGINE)
      .getWebapps()
      .getEndpoint();
  }

  @After
  public void cleanUp() {
    setWebappsEndpoint(defaultWebappsEndpoint);
    setWebappsEnabled(true);
  }

  @Test
  public void getCamundaWebappsEndpoint() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetCamundaWebappsEndpointRequest()
            .withoutAuthentication()
            .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void getDefaultCamundaWebappsEndpoint() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildGetCamundaWebappsEndpointRequest()
            .execute();

    // then
    Map<String, WebappsEndpointDto> webappsEndpoints =
      response.readEntity(new GenericType<Map<String, WebappsEndpointDto>>(){});
    assertThat(webappsEndpoints.size(), greaterThan(0));
    WebappsEndpointDto defaultEndpoint = webappsEndpoints.get("1");
    assertThat(defaultEndpoint, notNullValue());
    assertThat(defaultEndpoint.getEndpoint(), is("http://localhost:8080/camunda"));
    assertThat(defaultEndpoint.getEngineName(), is("default"));
  }

  @Test
  public void getCustomCamundaWebappsEndpoint() {
    // given
    setWebappsEndpoint("foo");

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetCamundaWebappsEndpointRequest()
            .execute();


    // then
    Map<String, WebappsEndpointDto> webappsEndpoints =
      response.readEntity(new GenericType<Map<String, WebappsEndpointDto>>(){});
    assertThat(webappsEndpoints.size(), greaterThan(0));
    WebappsEndpointDto defaultEndpoint = webappsEndpoints.get("1");
    assertThat(defaultEndpoint, notNullValue());
    assertThat(defaultEndpoint.getEndpoint(), is("foo"));
    assertThat(defaultEndpoint.getEngineName(), is("default"));
  }

  @Test
  public void disableWebappsEndpointReturnsEmptyEndpoint() {
    // given
    setWebappsEnabled(false);

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetCamundaWebappsEndpointRequest()
            .execute();


    // then
    Map<String, WebappsEndpointDto> webappsEndpoints =
      response.readEntity(new GenericType<Map<String, WebappsEndpointDto>>(){});
    assertThat(webappsEndpoints.size(), greaterThan(0));
    WebappsEndpointDto defaultEndpoint = webappsEndpoints.get("1");
    assertThat(defaultEndpoint, notNullValue());
    assertTrue(defaultEndpoint.getEndpoint().isEmpty());
  }

  private void setWebappsEndpoint(String webappsEndpoint) {
    embeddedOptimizeRule
      .getConfigurationService()
      .getConfiguredEngines()
      .get(DEFAULT_ENGINE)
      .getWebapps()
      .setEndpoint(webappsEndpoint);
  }

  private void setWebappsEnabled(boolean enabled) {
    embeddedOptimizeRule
      .getConfigurationService()
      .getConfiguredEngines()
      .get(DEFAULT_ENGINE)
      .getWebapps()
      .setEnabled(enabled);
  }
}