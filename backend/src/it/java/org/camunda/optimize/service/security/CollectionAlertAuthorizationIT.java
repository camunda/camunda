/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class CollectionAlertAuthorizationIT extends AbstractAlertIT {

  protected AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  private static final Stream<Integer> definitionTypes() {
    return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
  }

  private static final Stream<List<Integer>> definitionTypePairs() {
    return Stream.of(
      Arrays.asList(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION),
      Arrays.asList(RESOURCE_TYPE_DECISION_DEFINITION, RESOURCE_TYPE_PROCESS_DEFINITION)
    );
  }

  @ParameterizedTest
  @MethodSource("definitionTypes")
  public void getAlertsForAuthorizedCollection(final int definitionType) {
    // given
    final String collectionId1 = createNewCollection();
    final String reportId1 = createNumberReportForCollection(collectionId1, definitionType);
    final String reportId2 = createNumberReportForCollection(collectionId1, definitionType);
    final String alertId1 = createAlertForReport(reportId1);
    final String alertId2 = createAlertForReport(reportId1);
    final String alertId3 = createAlertForReport(reportId2);

    final String collectionId2 = createNewCollection();
    final String reportId3 = createNumberReportForCollection(collectionId2, definitionType);
    createAlertForReport(reportId3);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionType);
    addRoleToCollectionAsDefaultUser(new CollectionRoleDto(new UserDto(KERMIT_USER), RoleType.VIEWER), collectionId1);

    // when
    List<String> allAlertIds = getAlertsRequestAsKermit(collectionId1).executeAndReturnList(
      AlertDefinitionDto.class,
      200
    ).stream().map(alerts -> alerts.getId()).collect(toList());

    // then
    assertThat(allAlertIds, containsInAnyOrder(alertId1, alertId2, alertId3));
  }

  @ParameterizedTest
  @MethodSource("definitionTypePairs")
  public void getAlertsForPartiallyAuthorizedCollection(final List<Integer> typePair) {
    // given
    final String collectionId1 = createNewCollection();
    final String reportId1 = createNumberReportForCollection(collectionId1, typePair.get(0));
    final String reportId2 = createNumberReportForCollection(collectionId1, typePair.get(1));
    final String alertId1 = createAlertForReport(reportId1);
    final String alertId2 = createAlertForReport(reportId1);
    createAlertForReport(reportId2);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(typePair.get(0));
    addRoleToCollectionAsDefaultUser(new CollectionRoleDto(new UserDto(KERMIT_USER), RoleType.VIEWER), collectionId1);

    // when
    List<String> allAlertIds = getAlertsRequestAsKermit(collectionId1).executeAndReturnList(
      AlertDefinitionDto.class,
      200
    ).stream().map(alert -> alert.getId()).collect(toList());

    // then
    assertThat(allAlertIds, containsInAnyOrder(alertId1, alertId2));
  }

  @ParameterizedTest
  @MethodSource("definitionTypes")
  public void getAlertsForUnauthorizedCollection(final int definitionResourceType) {
    // given
    final String collectionId1 = createNewCollection();
    final String reportId1 = createNumberReportForCollection(collectionId1, definitionResourceType);
    final String reportId2 = createNumberReportForCollection(collectionId1, definitionResourceType);
    List<String> expectedAlertIdList = new ArrayList<>();
    expectedAlertIdList.add(createAlertForReport(reportId1));
    expectedAlertIdList.add(createAlertForReport(reportId1));
    expectedAlertIdList.add(createAlertForReport(reportId2));

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    // when
    Response response = getAlertsRequestAsKermit(collectionId1).execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  private OptimizeRequestExecutor getAlertsRequestAsKermit(final String collectionId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAlertsForCollectionRequest(collectionId)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER);
  }

  private String addRoleToCollectionAsDefaultUser(final CollectionRoleDto roleDto,
                                                  final String collectionId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute(IdDto.class, 200)
      .getId();
  }
}
