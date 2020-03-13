/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.collection;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static junit.framework.TestCase.assertTrue;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CollectionReportsAuthorizationIT extends AbstractIT {

  protected AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  private static Stream<Integer> definitionTypes() {
    return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
  }

  private static Stream<List<Integer>> definitionTypePairs() {
    return Stream.of(
      Arrays.asList(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION),
      Arrays.asList(RESOURCE_TYPE_DECISION_DEFINITION, RESOURCE_TYPE_PROCESS_DEFINITION)
    );
  }

  @ParameterizedTest
  @MethodSource("definitionTypes")
  public void getReportsForAuthorizedCollection(final int definitionType) {
    // given
    final String collectionId1 = collectionClient.createNewCollectionForAllDefinitionTypes();
    List<String> expectedReportIds = new ArrayList<>();
    expectedReportIds.add(createReportForCollection(collectionId1, definitionType));
    expectedReportIds.add(createReportForCollection(collectionId1, definitionType));

    final String collectionId2 = collectionClient.createNewCollectionForAllDefinitionTypes();
    createReportForCollection(collectionId2, definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionType);
    collectionClient.addRoleToCollection(collectionId1, new CollectionRoleDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.VIEWER
    ));

    // when
    List<AuthorizedReportDefinitionDto> reports = collectionClient.getReportsForCollectionAsUser(
      collectionId1,
      KERMIT_USER,
      KERMIT_USER
    );

    // then
    assertThat(reports.size(), is(expectedReportIds.size()));
    assertTrue(reports.stream()
                 .allMatch(reportDto -> expectedReportIds.contains(reportDto.getDefinitionDto().getId())));
  }

  @ParameterizedTest
  @MethodSource("definitionTypePairs")
  public void getReportsForPartiallyAuthorizedCollection(final List<Integer> typePair) {
    // given
    final String collectionId1 = collectionClient.createNewCollectionForAllDefinitionTypes();
    List<String> expectedReportIds = new ArrayList<>();
    expectedReportIds.add(createReportForCollection(collectionId1, typePair.get(0)));
    expectedReportIds.add(createReportForCollection(collectionId1, typePair.get(0)));
    createReportForCollection(collectionId1, typePair.get(1));

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(typePair.get(0));
    collectionClient.addRoleToCollection(collectionId1, new CollectionRoleDto(
      new IdentityDto(KERMIT_USER, IdentityType.USER),
      RoleType.VIEWER
    ));

    // when
    List<AuthorizedReportDefinitionDto> allAlerts = collectionClient.getReportsForCollectionAsUser(
      collectionId1,
      KERMIT_USER,
      KERMIT_USER
    );

    // then
    assertThat(allAlerts.size(), is(expectedReportIds.size()));
    assertTrue(allAlerts.stream()
                 .allMatch(reportDto -> expectedReportIds.contains(reportDto.getDefinitionDto().getId())));
  }

  @ParameterizedTest
  @MethodSource("definitionTypes")
  public void getReportsForUnauthorizedCollection(final int definitionResourceType) {
    // given
    final String collectionId1 = collectionClient.createNewCollectionForAllDefinitionTypes();
    List<String> expectedReportIds = new ArrayList<>();
    expectedReportIds.add(createReportForCollection(collectionId1, definitionResourceType));
    expectedReportIds.add(createReportForCollection(collectionId1, definitionResourceType));

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetReportsForCollectionRequest(collectionId1)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
  }

  private String createReportForCollection(final String collectionId, final int resourceType) {
    switch (resourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        SingleProcessReportDefinitionDto procReport = reportClient.createSingleProcessReportDefinitionDto(
          collectionId,
          DEFAULT_DEFINITION_KEY,
          DEFAULT_TENANTS
        );
        return reportClient.createSingleProcessReport(procReport);

      case RESOURCE_TYPE_DECISION_DEFINITION:
        SingleDecisionReportDefinitionDto decReport = reportClient.createSingleDecisionReportDefinitionDto(
          collectionId,
          DEFAULT_DEFINITION_KEY,
          DEFAULT_TENANTS
        );
        return reportClient.createSingleDecisionReport(decReport);

      default:
        throw new OptimizeRuntimeException("Unknown resource type provided.");
    }
  }
}
