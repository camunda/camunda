/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;

import static javax.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

public class MultiEngineUserTaskIdentityCacheServiceIT extends AbstractMultiEngineIT {
  public static final String ASSIGNEE_ID_JOHN = "john";
  public static final String JOHN_FIRST_NAME = "The";
  public static final String JOHN_LAST_NAME = "Imposter";

  public static final String ASSIGNEE_ID_JEAN = "jean";
  public static final String JEAN_FIRST_NAME = "True";
  public static final String JEAN_LAST_NAME = "CrewMember";

  public static final String CANDIDATE_GROUP_ID_IMPOSTERS = "imposters";
  public static final String CANDIDATE_GROUP_NAME_IMPOSTERS = "The Evil Imposters";

  public static final String CANDIDATE_GROUP_ID_CREW_MEMBERS = "crewMembers";
  public static final String CANDIDATE_GROUP_NAME_CREW_MEMBERS = "The Crew Members";

  @BeforeEach
  public void beforeEach() {
    getIdentityCacheService().stopScheduledSync();
  }

  @AfterEach
  public void afterEach() {
    getIdentityCacheService().startScheduledSync();
  }

  @Test
  public void testAssigneesFromAllEnginesAreImported() {
    // given
    addSecondEngineToConfiguration();
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    secondaryEngineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN, engineIntegrationExtension);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN, secondaryEngineIntegrationExtension);

    // when
    getIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isPresent();
    assertThat(getIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JEAN)).isPresent();
  }

  @Test
  public void testAssigneesFromAllEnginesAreImportedBrokenEngineIsSkipped() {
    // given
    addSecondEngineToConfiguration();
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    secondaryEngineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN, engineIntegrationExtension);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN, secondaryEngineIntegrationExtension);

    final ClientAndServer firstEngineMockServer =
      useAndGetMockServerForEngine(engineIntegrationExtension.getEngineName());

    final HttpRequest getUserRequest = request().withPath(".*/user/.*").withMethod(GET);
    firstEngineMockServer.when(getUserRequest).error(HttpError.error().withDropConnection(true));

    // when
    getIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isNotPresent();
    assertThat(getIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JEAN)).isPresent();
    firstEngineMockServer.verify(getUserRequest);
  }

  @Test
  public void testCandidateGroupsFromAllEnginesAreImported() {
    // given
    addSecondEngineToConfiguration();
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    secondaryEngineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(
      CANDIDATE_GROUP_ID_IMPOSTERS, engineIntegrationExtension
    );
    startSimpleUserTaskProcessWithCandidateGroupAndImport(
      CANDIDATE_GROUP_ID_CREW_MEMBERS, secondaryEngineIntegrationExtension
    );

    // when
    getIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_IMPOSTERS)).isPresent();
    assertThat(getIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS)).isPresent();
  }

  @Test
  public void testCandidateGroupsFromAllEnginesAreImportedBrokenEngineIsSkipped() {
    // given
    addSecondEngineToConfiguration();
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    secondaryEngineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(
      CANDIDATE_GROUP_ID_IMPOSTERS, engineIntegrationExtension
    );
    startSimpleUserTaskProcessWithCandidateGroupAndImport(
      CANDIDATE_GROUP_ID_CREW_MEMBERS, secondaryEngineIntegrationExtension
    );

    final ClientAndServer firstEngineMockServer =
      useAndGetMockServerForEngine(engineIntegrationExtension.getEngineName());

    final HttpRequest getUserRequest = request().withPath(".*/group/.*").withMethod(GET);
    firstEngineMockServer.when(getUserRequest).error(HttpError.error().withDropConnection(true));

    // when
    getIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_IMPOSTERS)).isNotPresent();
    assertThat(getIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS)).isPresent();
    firstEngineMockServer.verify(getUserRequest);
  }

  private void startSimpleUserTaskProcessWithAssigneeAndImport(final String assignee,
                                                                                  final EngineIntegrationExtension engine) {
    engine.deployAndStartProcess(BpmnModels.getUserTaskDiagramWithAssignee(assignee));
    importAllEngineEntitiesFromScratch();
  }

  private void startSimpleUserTaskProcessWithCandidateGroupAndImport(final String candidateGroup,
                                                                                        final EngineIntegrationExtension engine) {
    engine.deployAndStartProcess(BpmnModels.getUserTaskDiagramWithCandidateGroup(candidateGroup));
    importAllEngineEntitiesFromScratch();
  }

  private PlatformUserTaskIdentityCache getIdentityCacheService() {
    return embeddedOptimizeExtension.getUserTaskIdentityCache();
  }

}
