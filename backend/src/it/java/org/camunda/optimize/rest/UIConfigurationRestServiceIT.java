/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.ui_configuration.HeaderCustomizationDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.WebappsEndpointDto;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.service.util.configuration.ui.TextColorType;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule.DEFAULT_ENGINE_ALIAS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

public class UIConfigurationRestServiceIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  private EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineIntegrationRule).around(embeddedOptimizeRule);

  @Test
  public void getHeaderCustomization() {
    // when
    final UIConfigurationDto actualConfiguration = getUIConfiguration();

    // then
    HeaderCustomizationDto configurationHeader = actualConfiguration.getHeader();
    assertThat(configurationHeader, notNullValue());
    assertThat(configurationHeader.getTextColor(), is(TextColorType.DARK));
    assertThat(configurationHeader.getBackgroundColor(), is("#FFFFFF"));
    assertThat(configurationHeader.getLogo(), startsWith("data:"));
  }

  @Test
  public void sharingEnabled() {
    // given
    embeddedOptimizeRule.getConfigurationService().setSharingEnabled(true);

    // when
    final UIConfigurationDto response = getUIConfiguration();

    // then
    assertThat(response.isSharingEnabled(), is(true));
  }

  @Test
  public void sharingDisabled() {
    // given
    embeddedOptimizeRule.getConfigurationService().setSharingEnabled(false);

    // when
    final UIConfigurationDto response = getUIConfiguration();

    // then
    assertThat(response.isSharingEnabled(), is(false));
  }

  @Test
  public void getDefaultCamundaWebappsEndpoint() {
    // when
    UIConfigurationDto response = getUIConfiguration();

    // then
    Map<String, WebappsEndpointDto> webappsEndpoints = response.getWebappsEndpoints();
    assertThat(webappsEndpoints.size(), greaterThan(0));
    WebappsEndpointDto defaultEndpoint = webappsEndpoints.get(DEFAULT_ENGINE_ALIAS);
    assertThat(defaultEndpoint, Matchers.notNullValue());
    assertThat(defaultEndpoint.getEndpoint(), is("http://localhost:8080/camunda"));
    assertThat(defaultEndpoint.getEngineName(), is(engineIntegrationRule.getEngineName()));
  }

  @Test
  public void getCustomCamundaWebappsEndpoint() {
    // given
    setWebappsEndpoint("foo");

    // when
    UIConfigurationDto response = getUIConfiguration();


    // then
    Map<String, WebappsEndpointDto> webappsEndpoints = response.getWebappsEndpoints();
    assertThat(webappsEndpoints.size(), greaterThan(0));
    WebappsEndpointDto defaultEndpoint = webappsEndpoints.get(DEFAULT_ENGINE_ALIAS);
    assertThat(defaultEndpoint, Matchers.notNullValue());
    assertThat(defaultEndpoint.getEndpoint(), is("foo"));
    assertThat(defaultEndpoint.getEngineName(), is(engineIntegrationRule.getEngineName()));
  }

  @Test
  public void disableWebappsEndpointReturnsEmptyEndpoint() {
    // given
    setWebappsEnabled(false);

    // when
    UIConfigurationDto response = getUIConfiguration();


    // then
    Map<String, WebappsEndpointDto> webappsEndpoints = response.getWebappsEndpoints();
    assertThat(webappsEndpoints.size(), greaterThan(0));
    WebappsEndpointDto defaultEndpoint = webappsEndpoints.get(DEFAULT_ENGINE_ALIAS);
    assertThat(defaultEndpoint, Matchers.notNullValue());
    assertTrue(defaultEndpoint.getEndpoint().isEmpty());
  }

  @Test
  public void emailNotificationIsEnabled() {
    //given
    embeddedOptimizeRule.getConfigurationService().setEmailEnabled(true);

    // when
    UIConfigurationDto response = getUIConfiguration();

    // then
    assertThat(response.isEmailEnabled(), is(true));
  }

  @Test
  public void emailNotificationIsDisabled() {
    //given
    embeddedOptimizeRule.getConfigurationService().setEmailEnabled(false);

    // when
    UIConfigurationDto response = getUIConfiguration();

    // then
    assertThat(response.isEmailEnabled(), is(false));
  }

  @Test
  public void getOptimizeVersion() {
    // when
    UIConfigurationDto response = getUIConfiguration();

    // then
    assertThat(response.getOptimizeVersion(), is(Version.RAW_VERSION));
  }

  private UIConfigurationDto getUIConfiguration() {
    return embeddedOptimizeRule.getRequestExecutor()
      .withoutAuthentication()
      .buildGetUIConfigurationRequest()
      .execute(UIConfigurationDto.class, 200);
  }

  private void setWebappsEndpoint(String webappsEndpoint) {
    embeddedOptimizeRule
      .getConfigurationService()
      .getConfiguredEngines()
      .get(DEFAULT_ENGINE_ALIAS)
      .getWebapps()
      .setEndpoint(webappsEndpoint);
  }

  private void setWebappsEnabled(boolean enabled) {
    embeddedOptimizeRule
      .getConfigurationService()
      .getConfiguredEngines()
      .get(DEFAULT_ENGINE_ALIAS)
      .getWebapps()
      .setEnabled(enabled);
  }
}
