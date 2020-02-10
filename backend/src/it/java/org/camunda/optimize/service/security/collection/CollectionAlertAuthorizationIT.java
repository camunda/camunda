/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.collection;

import com.google.common.collect.ImmutableMap;
import org.assertj.core.api.SoftAssertions;
import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class CollectionAlertAuthorizationIT extends AbstractAlertIT {

  private static final String KERMIT_USER = "kermit";
  private static final String MISS_PIGGY_USER = "MissPiggy";

  protected AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  private static Stream<DefinitionType> definitionTypes() {
    return Stream.of(PROCESS, DefinitionType.DECISION);
  }


  private static Stream<List<DefinitionType>> definitionTypePairs() {
    return Stream.of(
      Arrays.asList(PROCESS, DECISION),
      Arrays.asList(DECISION, PROCESS)
    );
  }

  private ImmutableMap<DefinitionType, Integer> definitionTypeToResourceType =
    ImmutableMap.of(PROCESS, RESOURCE_TYPE_PROCESS_DEFINITION,
                    DECISION, RESOURCE_TYPE_DECISION_DEFINITION
    );

  @ParameterizedTest
  @MethodSource("definitionTypes")
  public void getAlertsForAuthorizedCollection(final DefinitionType definitionType) {
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

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionTypeToResourceType.get(definitionType));
    addRoleToCollectionAsDefaultUser(new CollectionRoleDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.VIEWER
    ), collectionId1);

    // when
    List<String> allAlertIds = collectionClient.getAlertsRequest(KERMIT_USER, KERMIT_USER, collectionId1)
      .executeAndReturnList(
        AlertDefinitionDto.class,
        Response.Status.OK.getStatusCode()
      )
      .stream()
      .map(AlertDefinitionDto::getId)
      .collect(toList());

    // then
    assertThat(allAlertIds, containsInAnyOrder(alertId1, alertId2, alertId3));
  }

  @ParameterizedTest
  @MethodSource("definitionTypePairs")
  public void getAlertsForPartiallyAuthorizedCollection(final List<DefinitionType> typePair) {
    // given
    final String collectionId1 = collectionClient.createNewCollectionWithDefaultScope(typePair.get(0));
    collectionClient.createScopeWithTenants(collectionId1, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS, typePair.get(1));
    final String reportId1 = createNumberReportForCollection(collectionId1, typePair.get(0));
    final String reportId2 = createNumberReportForCollection(collectionId1, typePair.get(1));
    final String alertId1 = createAlertForReport(reportId1);
    final String alertId2 = createAlertForReport(reportId1);
    createAlertForReport(reportId2);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionTypeToResourceType.get(typePair.get(0)));
    addRoleToCollectionAsDefaultUser(
      new CollectionRoleDto(
        new IdentityDto(KERMIT_USER, IdentityType.USER), RoleType.VIEWER),
      collectionId1
    );

    // when
    List<String> allAlertIds = collectionClient.getAlertsRequest(KERMIT_USER, KERMIT_USER, collectionId1)
      .executeAndReturnList(
        AlertDefinitionDto.class,
        Response.Status.OK.getStatusCode()
      )
      .stream()
      .map(AlertDefinitionDto::getId)
      .collect(toList());

    // then
    assertThat(allAlertIds, containsInAnyOrder(alertId1, alertId2));
  }

  @ParameterizedTest
  @MethodSource("definitionTypes")
  public void getAlertsForUnauthorizedCollection(final DefinitionType definitionType) {
    // given
    final String collectionId1 = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    createNumberReportForCollection(collectionId1, definitionType);
    createNumberReportForCollection(collectionId1, definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionTypeToResourceType.get(definitionType));

    // when
    Response response = collectionClient.getAlertsRequest(KERMIT_USER, KERMIT_USER, collectionId1).execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @ParameterizedTest(name = "viewers of a collection are not allowed to edit, delete or create alerts for reports of " +
    "definition type {0}")
  @MethodSource("definitionTypes")
  public void viewersNotAllowedToUpdateOrDeleteOrCreateAlerts(final DefinitionType definitionType) {
    // given
    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    final String reportId = createNumberReportForCollection(collectionId, definitionType);
    final String alertId = createAlertForReport(reportId);
    final AlertCreationDto alertCreationDto = createSimpleAlert(reportId);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionTypeToResourceType.get(definitionType));
    addRoleToCollectionAsDefaultUser(new CollectionRoleDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.VIEWER
    ), collectionId);

    // when
    Response createResponse = alertClient.createAlertAsUser(alertCreationDto, KERMIT_USER, KERMIT_USER);
    Response editResponse = alertClient.editAlertAsUser(alertId, alertCreationDto, KERMIT_USER, KERMIT_USER);
    Response deleteResponse = alertClient.deleteAlertAsUser(alertId, KERMIT_USER, KERMIT_USER);

    // then
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(createResponse.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    softly.assertThat(editResponse.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    softly.assertThat(deleteResponse.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest(name = "Editors and managers of a collection are allowed to edit, delete and create alerts for " +
    "reports of definition type {0}")
  @MethodSource("definitionTypes")
  public void nonViewersAllowedToUpdateOrDeleteOrCreateAlerts(final DefinitionType definitionType) {
    // given
    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    final String reportId = createNumberReportForCollection(collectionId, definitionType);
    final String alertId1 = createAlertForReport(reportId);
    final String alertId2 = createAlertForReport(reportId);
    final AlertCreationDto alertCreationDto = createSimpleAlert(reportId);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addUserAndGrantOptimizeAccess(MISS_PIGGY_USER);
    authorizationClient.addGlobalAuthorizationForResource(definitionTypeToResourceType.get(definitionType));
    addRoleToCollectionAsDefaultUser(new CollectionRoleDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.MANAGER
    ), collectionId);
    addRoleToCollectionAsDefaultUser(
      new CollectionRoleDto(
        new IdentityDto(MISS_PIGGY_USER, IdentityType.USER),
        RoleType.EDITOR
      ),
      collectionId
    );

    // when
    Response managerCreateResponse = alertClient.createAlertAsUser(alertCreationDto, KERMIT_USER, KERMIT_USER);
    Response managerEditResponse = alertClient.editAlertAsUser(alertId1, alertCreationDto, KERMIT_USER, KERMIT_USER);
    Response managerDeleteResponse = alertClient.deleteAlertAsUser(alertId1, KERMIT_USER, KERMIT_USER);

    Response editorCreateResponse = alertClient.createAlertAsUser(alertCreationDto, MISS_PIGGY_USER, MISS_PIGGY_USER);
    Response editorEditResponse = alertClient.editAlertAsUser(
      alertId2,
      alertCreationDto,
      MISS_PIGGY_USER,
      MISS_PIGGY_USER
    );
    Response editorDeleteResponse = alertClient.deleteAlertAsUser(alertId2, MISS_PIGGY_USER, MISS_PIGGY_USER);

    // then
    SoftAssertions softly = new SoftAssertions();

    softly.assertThat(managerCreateResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    softly.assertThat(managerEditResponse.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    softly.assertThat(managerDeleteResponse.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    softly.assertThat(editorCreateResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    softly.assertThat(editorEditResponse.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    softly.assertThat(editorDeleteResponse.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  private void addRoleToCollectionAsDefaultUser(final CollectionRoleDto roleDto,
                                                final String collectionId) {
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());
  }

}
