/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule.DEFAULT_ENGINE_ALIAS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class StatusRestServiceIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Test
  public void getConnectedStatus() {
    final StatusWithProgressDto statusWithProgressDto = embeddedOptimizeExtensionRule.getRequestExecutor()
      .withoutAuthentication()
      .buildCheckImportStatusRequest()
      .execute(StatusWithProgressDto.class, 200);

    assertThat(statusWithProgressDto.getConnectionStatus().isConnectedToElasticsearch(), is(true));
    assertThat(statusWithProgressDto.getConnectionStatus().getEngineConnections().size(), is(1));
    assertThat(statusWithProgressDto.getConnectionStatus().getEngineConnections().get(DEFAULT_ENGINE_ALIAS), is(true));
  }

  @Test
  public void getImportStatus() {
    final StatusWithProgressDto statusWithProgressDto = embeddedOptimizeExtensionRule.getRequestExecutor()
      .withoutAuthentication()
      .buildCheckImportStatusRequest()
      .execute(StatusWithProgressDto.class, 200);

    assertThat(statusWithProgressDto.getIsImporting().keySet(), contains(DEFAULT_ENGINE_ALIAS));
  }

  @Test
  public void importStatusIsTrueWhenImporting() {
    // given
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();

    // when
    final StatusWithProgressDto status = embeddedOptimizeExtensionRule.getRequestExecutor()
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
    final StatusWithProgressDto status = embeddedOptimizeExtensionRule.getRequestExecutor()
      .withoutAuthentication()
      .buildCheckImportStatusRequest()
      .execute(StatusWithProgressDto.class, 200);

    // then
    final Map<String, Boolean> isImportingMap = status.getIsImporting();
    assertThat(isImportingMap, is(notNullValue()));
    assertThat(isImportingMap.get("1"), is(false));
  }
}
