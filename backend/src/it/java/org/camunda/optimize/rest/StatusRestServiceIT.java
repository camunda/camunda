/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class StatusRestServiceIT {
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  private EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineIntegrationRule).around(embeddedOptimizeRule);

  @Test
  public void getImportStatus() {
    embeddedOptimizeRule.getRequestExecutor()
      .withoutAuthentication()
      .buildCheckImportStatusRequest()
      .execute(StatusWithProgressDto.class, 200);
  }


  @Test
  public void importStatusIsTrueWhenImporting() {
    // given
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();

    // when
    final StatusWithProgressDto status = embeddedOptimizeRule.getRequestExecutor()
      .withoutAuthentication()
      .buildCheckImportStatusRequest()
      .execute(StatusWithProgressDto.class, 200);

    // then
    final Map<String, Boolean> isImportingMap = status.getIsImporting();
    assertThat(isImportingMap, is(notNullValue()));
    assertThat(isImportingMap.get("1"), is(true));
  }

  @Test
  public void importStatusIsFalseWhenNotImporting() {
    // when
    final StatusWithProgressDto status = embeddedOptimizeRule.getRequestExecutor()
      .withoutAuthentication()
      .buildCheckImportStatusRequest()
      .execute(StatusWithProgressDto.class, 200);

    // then
    final Map<String, Boolean> isImportingMap = status.getIsImporting();
    assertThat(isImportingMap, is(notNullValue()));
    assertThat(isImportingMap.get("1"), is(false));
  }
}
