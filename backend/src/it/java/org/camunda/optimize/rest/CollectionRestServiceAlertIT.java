/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class CollectionRestServiceAlertIT extends AbstractAlertIT {

  private static Stream<Integer> definitionType() {
    return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
  }

  @ParameterizedTest(name = "get stored alerts for collection with different reports for definition type {0}")
  @MethodSource("definitionType")
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
      .map(AlertDefinitionDto::getId)
      .collect(toList());

    // then
    assertThat(allAlertIds, containsInAnyOrder(alertId1, alertId2, alertId3));
  }

  @ParameterizedTest(name = "only alerts in given collection should be retrieved for definition type {0}")
  @MethodSource("definitionType")
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
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAlertsForCollectionRequest(collectionId)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .executeAndReturnList(AlertDefinitionDto.class, 200);
  }
}
