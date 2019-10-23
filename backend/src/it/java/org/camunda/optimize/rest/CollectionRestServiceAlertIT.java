/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

@RunWith(JUnitParamsRunner.class)
public class CollectionRestServiceAlertIT extends AbstractAlertIT {

  private static final Object[] definitionType() {
    return new Object[]{RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION};
  }

  @Test
  @Parameters(method = "definitionType")
  public void getStoredAlerts(final int definitionResourceType) {
    // given
    final String collectionId1 = createNewCollection();
    final String reportId1 = createNumberReportForCollection(collectionId1, definitionResourceType);
    final String reportId2 = createNumberReportForCollection(collectionId1, definitionResourceType);
    final String alertId1 = createAlertForReport(reportId1);
    final String alertId2 = createAlertForReport(reportId1);
    final String alertId3 = createAlertForReport(reportId2);

    final String collectionId2 = createNewCollection();
    final String reportId3 = createNumberReportForCollection(collectionId2, definitionResourceType);
    createAlertForReport(reportId3);

    // when
    List<String> allAlertIds = getAlertsForCollectionAsDefaultUser(collectionId1).stream()
      .map(alert -> alert.getId())
      .collect(toList());

    // then
    assertThat(allAlertIds, containsInAnyOrder(alertId1, alertId2, alertId3));
  }

  @Test
  @Parameters(method = "definitionType")
  public void getNoneStoredAlerts(final int definitionResourceType) {
    // given
    final String collectionId1 = createNewCollection();
    final String collectionId2 = createNewCollection();
    final String reportId1 = createNumberReportForCollection(collectionId1, definitionResourceType);
    createNumberReportForCollection(collectionId2, definitionResourceType);
    createAlertForReport(reportId1);

    // when
    List<AlertDefinitionDto> allAlerts = getAlertsForCollectionAsDefaultUser(collectionId2);

    // then
    assertThat(allAlerts.size(), is(0));
  }

  private List<AlertDefinitionDto> getAlertsForCollectionAsDefaultUser(final String collectionId) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetAlertsForCollectionRequest(collectionId)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .executeAndReturnList(AlertDefinitionDto.class, 200);
  }
}
