/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class LocalizationRestServiceIT {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain.outerRule(engineIntegrationRule).around(embeddedOptimizeRule);

  @Test
  public void getEnglishLocale() {
    //given
    final String localeCode = "en";
    final JsonNode expectedLocaleJson = getExpectedJsonFileForLocale(localeCode);

    // when
    final JsonNode localeJson = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetLocalizationRequest(localeCode)
      .execute(JsonNode.class, 200);

    // then
    assertThat(localeJson, is(expectedLocaleJson));

  }

  @Test
  public void getGermanLocale() {
    //given
    final String localeCode = "de";
    final JsonNode expectedLocaleJson = getExpectedJsonFileForLocale(localeCode);

    // when
    final JsonNode localeJson = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetLocalizationRequest(localeCode)
      .execute(JsonNode.class, 200);

    // then
    assertThat(localeJson, is(expectedLocaleJson));
  }

  @Test
  public void getFallbackLocaleForInvalidCode() {
    //given
    final String localeCode = "xyz";
    final JsonNode expectedLocaleJson = getExpectedJsonFileForLocale(
      embeddedOptimizeRule.getConfigurationService().getFallbackLocale()
    );

    // when
    final JsonNode localeJson = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetLocalizationRequest(localeCode)
      .execute(JsonNode.class, 200);

    // then
    assertThat(localeJson, is(expectedLocaleJson));
  }

  @Test
  public void getFallbackLocaleForMissingCode() {
    //given
    final JsonNode expectedLocaleJson = getExpectedJsonFileForLocale(
      embeddedOptimizeRule.getConfigurationService().getFallbackLocale()
    );

    // when
    final JsonNode localeJson = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetLocalizationRequest(null)
      .execute(JsonNode.class, 200);

    // then
    assertThat(localeJson, is(expectedLocaleJson));
  }

  @Test
  public void get500OnFileGone() {
    //given
    final String localeCode = "xyz";
    embeddedOptimizeRule.getConfigurationService().getAvailableLocales().add(localeCode);

    // when
    final Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetLocalizationRequest(localeCode)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @SneakyThrows
  private JsonNode getExpectedJsonFileForLocale(final String locale) {
    return OBJECT_MAPPER.readValue(
      getClass().getClassLoader().getResourceAsStream(LocalizationService.LOCALIZATION_PATH + locale + ".json"),
      JsonNode.class
    );
  }
}
