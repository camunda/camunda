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

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ALERT_INDEX_NAME;

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
    final String alertId1 = alertClient.createAlertForReport(reportId1);
    final String alertId2 = alertClient.createAlertForReport(reportId1);
    final String alertId3 = alertClient.createAlertForReport(reportId2);

    final String collectionId2 = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    final String reportId3 = createNumberReportForCollection(collectionId2, definitionType);
    alertClient.createAlertForReport(reportId3);

    // when
    List<AlertDefinitionDto> allAlerts = alertClient.getAlertsForCollectionAsDefaultUser(collectionId1);

    // then
    assertThat(allAlerts)
      .extracting(alert -> alert.getId())
      .containsExactlyInAnyOrder(alertId1, alertId2, alertId3);
    assertThat(allAlerts).allMatch(alert -> alert.getOwner().equals(DEFAULT_FULLNAME));
    assertThat(allAlerts).allMatch(alert -> alert.getLastModifier().equals(DEFAULT_FULLNAME));
  }

  @ParameterizedTest(name = "only alerts in given collection should be retrieved for definition type {0}")
  @MethodSource("definitionType")
  public void getNoneStoredAlerts(final DefinitionType definitionType) {
    // given
    final String collectionId1 = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    final String collectionId2 = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    final String reportId1 = createNumberReportForCollection(collectionId1, definitionType);
    createNumberReportForCollection(collectionId2, definitionType);
    alertClient.createAlertForReport(reportId1);

    // when
    List<AlertDefinitionDto> allAlerts = alertClient.getAlertsForCollectionAsDefaultUser(collectionId2);

    // then
    assertThat(allAlerts).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void deleteCollectionAlsoDeletesContainingAlerts(final DefinitionType definitionType) {
    // given
    List<String> expectedAlertIds = new ArrayList<>();
    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);

    final String reportId1 = createNumberReportForCollection(collectionId, definitionType);
    final String reportId2 = createNumberReportForCollection(collectionId, definitionType);

    expectedAlertIds.add(alertClient.createAlertForReport(reportId1));
    expectedAlertIds.add(alertClient.createAlertForReport(reportId1));
    expectedAlertIds.add(alertClient.createAlertForReport(reportId2));

    // when
    collectionClient.deleteCollection(collectionId);

    Integer alertCount = elasticSearchIntegrationTestExtension.getDocumentCountOf(ALERT_INDEX_NAME);

    // then
    assertThat(alertCount).isEqualTo(0);
  }

}
