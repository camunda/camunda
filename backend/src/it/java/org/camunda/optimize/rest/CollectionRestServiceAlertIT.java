/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ALERT_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class CollectionRestServiceAlertIT extends AbstractAlertIT {

  private static Stream<DefinitionType> definitionType() {
    return Stream.of(DefinitionType.PROCESS, DefinitionType.DECISION);
  }

  @ParameterizedTest(name = "get stored alerts for collection with different reports for definition type {0}")
  @MethodSource("definitionType")
  public void getStoredAlerts(final DefinitionType definitionType) {
    // given
    final String collectionId1 = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    final String reportId1 = createNumberReportForCollection(collectionId1, definitionType);
    final String reportId2 = createNumberReportForCollection(collectionId1, definitionType);
    final String alertId1 = createAlertForReport(reportId1);
    final String alertId2 = createAlertForReport(reportId1);
    final String alertId3 = createAlertForReport(reportId2);

    final String collectionId2 = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    final String reportId3 = createNumberReportForCollection(collectionId2, definitionType);
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
  public void getNoneStoredAlerts(final DefinitionType definitionType) {
    // given
    final String collectionId1 = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    final String collectionId2 = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    final String reportId1 = createNumberReportForCollection(collectionId1, definitionType);
    createNumberReportForCollection(collectionId2, definitionType);
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

  @ParameterizedTest(name = "deleting a collection with reports of definition type {0} also deletes associated alerts")
  @MethodSource("definitionType")
  public void deleteCollectionAlsoDeletesContainingAlerts(final DefinitionType definitionType) {
    // given
    List<String> expectedAlertIds = new ArrayList<>();
    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);

    final String reportId1 = createNumberReportForCollection(collectionId, definitionType);
    final String reportId2 = createNumberReportForCollection(collectionId, definitionType);

    expectedAlertIds.add(createAlertForReport(reportId1));
    expectedAlertIds.add(createAlertForReport(reportId1));
    expectedAlertIds.add(createAlertForReport(reportId2));

    // when
    collectionClient.deleteCollection(collectionId);

    Integer alertCount = elasticSearchIntegrationTestExtension.getDocumentCountOf(ALERT_INDEX_NAME);

    // then
    assertThat(alertCount, is(0));
  }
}
