/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.identity;

import com.google.common.collect.ImmutableList;
import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.UserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.MaxEntryLimitHitException;
import org.camunda.optimize.service.es.job.importing.IdentityLinkLogImportJob;
import org.camunda.optimize.service.util.configuration.engine.UserTaskIdentityCacheConfiguration;
import org.camunda.optimize.util.BpmnModels;
import org.camunda.optimize.util.SuppressionConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import java.util.List;

import static javax.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_EMAIL_DOMAIN;
import static org.mockserver.model.HttpRequest.request;

public class UserTaskIdentityCacheServiceIT extends AbstractIT {
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

  @RegisterExtension
  protected final LogCapturer assigneeCandidateCacheServiceLogger =
    LogCapturer.create().captureForType(UserTaskIdentityCacheService.class);

  @RegisterExtension
  protected final LogCapturer identityLinkLogImportJobLog =
    LogCapturer.create().captureForType(IdentityLinkLogImportJob.class);

  @BeforeEach
  public void beforeEach() {
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
    getUserTaskIdentityCacheService().stopScheduledSync();
    getUserTaskIdentityCacheService().resetCache();
  }

  @AfterEach
  public void afterEach() {
    getUserTaskIdentityCacheService().startScheduledSync();
  }

  @Test
  public void syncReplacesCache() {
    // given
    getUserTaskIdentityCacheService().synchronizeIdentities();
    assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isEmpty();

    // when
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    getUserTaskIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isPresent();
  }

  @Test
  public void syncFailsOnCacheLimitHit() {
    // given
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    getUserTaskIdentityCacheService().synchronizeIdentities();

    // when
    // we have at least two users, but limit is now 1
    getUserTaskIdentityCacheConfiguration().setMaxEntryLimit(1L);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);

    final UserTaskIdentityCacheService assigneeIdentityCacheService = getUserTaskIdentityCacheService();
    assertThatThrownBy(assigneeIdentityCacheService::synchronizeIdentities)
    // then
      .isInstanceOf(MaxEntryLimitHitException.class);
    assertThat(assigneeIdentityCacheService.getUserIdentityById(ASSIGNEE_ID_JOHN)).isPresent();
    assertThat(assigneeIdentityCacheService.getUserIdentityById(ASSIGNEE_ID_JEAN)).isNotPresent();
  }

  @Test
  public void importDoesNotFailOnCacheLimitHit() {
    // given
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    getUserTaskIdentityCacheService().synchronizeIdentities();

    // when
    // we have at least two users, but limit is now 1
    getUserTaskIdentityCacheConfiguration().setMaxEntryLimit(1L);
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);

    // then
    identityLinkLogImportJobLog.assertContains(
      "Failed forwarding identities to assignee & candidate group service"
    );
    assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isPresent();
    assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JEAN)).isNotPresent();

    final List<ProcessInstanceDto> processInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(processInstances)
      .hasSize(2)
      .flatExtracting(ProcessInstanceDto::getUserTasks)
      .extracting(UserTaskInstanceDto::getAssignee)
      .containsExactlyInAnyOrder(ASSIGNEE_ID_JOHN, ASSIGNEE_ID_JEAN);
  }

  @Test
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void assigneeIsPresentWithMetadataAfterSync() {
    // given
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    getUserTaskIdentityCacheService().resetCache();

    // when
    getUserTaskIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
      .isPresent()
      .get()
      .extracting(UserDto::getFirstName, UserDto::getLastName, UserDto::getEmail)
      .containsExactly(JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN);
  }

  @Test
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void assingeeIsPresentWithoutMetadataAfterSyncIfMetadataDisabled() {
    // given
    getUserTaskIdentityCacheService().resetCache();
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    getUserTaskIdentityCacheConfiguration().setIncludeUserMetaData(false);

    // when
    getUserTaskIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
      .isPresent()
      .get()
      .extracting(UserDto::getFirstName, UserDto::getLastName, UserDto::getEmail)
      .containsExactly(null, null, null);
  }

  @Test
  public void twoAssigneesFromSameProcessInstanceArePresentAfterSync() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = engineIntegrationExtension.deployAndStartProcess(
      BpmnModels.getDoubleUserTaskDiagramWithAssignees(ASSIGNEE_ID_JOHN, ASSIGNEE_ID_JEAN)
    );
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceEngineDto.getId());
    importAllEngineEntitiesFromScratch();

    // when
    getUserTaskIdentityCacheService().synchronizeIdentities();

    // then
    final IdentitySearchResultResponseDto allEntries = getUserTaskIdentityCacheService().searchIdentities("", 10);
    assertThat(allEntries.getTotal()).isEqualTo(2);
    assertThat(allEntries.getResult())
      .extracting(IdentityWithMetadataResponseDto::toIdentityDto)
      .extracting(IdentityDto::getId)
      .containsExactlyInAnyOrder(ASSIGNEE_ID_JOHN, ASSIGNEE_ID_JEAN);
  }

  @Test
  public void userNotPresentAsAssigneeIsNotPresentAfterSync() {
    // given
    // users are added via #beforeEach

    // when
    getUserTaskIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isNotPresent();
  }

  @Test
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void assigneeIsPresentWithMetadataAfterImport() {
    // given
    getUserTaskIdentityCacheService().resetCache();

    // when
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);

    // then
    assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
      .isPresent()
      .get()
      .extracting(UserDto::getFirstName, UserDto::getLastName, UserDto::getEmail)
      .containsExactly(JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN);
  }

  @Test
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void assigneeIsPresentWithoutMetadataAfterImportIfMetadataDisabled() {
    // given
    getUserTaskIdentityCacheService().resetCache();
    getUserTaskIdentityCacheConfiguration().setIncludeUserMetaData(false);

    // when
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);

    // then
    assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
      .isPresent()
      .get()
      .extracting(UserDto::getFirstName, UserDto::getLastName, UserDto::getEmail)
      .containsExactly(null, null, null);
  }

  @Test
  public void assigneeUserIsNotFetchedOnImportIfAlreadyPresentInCache() {
    // given
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);

    // when
    final ClientAndServer firstEngineMockServer =
      useAndGetMockServerForEngine(engineIntegrationExtension.getEngineName());
    final HttpRequest getUserRequest = request().withPath(".*/user/.*").withMethod(GET);

    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);

    // then
    firstEngineMockServer.verify(getUserRequest, VerificationTimes.exactly(0));
    assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isPresent();
  }

  @Test
  public void importCompletesEvenIfGettingAssigneesFails() {
    // given
    getUserTaskIdentityCacheService().resetCache();
    final ClientAndServer firstEngineMockServer =
      useAndGetMockServerForEngine(engineIntegrationExtension.getEngineName());

    final HttpRequest getUserRequest = request().withPath(".*/user/.*").withMethod(GET);
    firstEngineMockServer.when(getUserRequest).error(HttpError.error().withDropConnection(true));

    // when
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);

    // then
    assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isNotPresent();
    firstEngineMockServer.verify(getUserRequest);
    assigneeCandidateCacheServiceLogger.assertContains(
      "Failed to resolve and add assignee/candidateGroup identities from engine camunda-bpm"
    );
  }

  @Test
  public void candidateGroupIsPresentAfterSync() {
    // given
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // when
    getUserTaskIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserTaskIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS))
      .isPresent()
      .get()
      .extracting(GroupDto::getName)
      .isEqualTo(CANDIDATE_GROUP_NAME_CREW_MEMBERS);
  }

  @Test
  public void twoCandidateGroupsFromSameProcessInstanceArePresentAfterSync() {
    // given
    engineIntegrationExtension.deployAndStartProcess(BpmnModels.getUserTaskDiagramWithMultipleCandidateGroups(
      ImmutableList.of(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_ID_CREW_MEMBERS)
    ));
    importAllEngineEntitiesFromScratch();

    // when
    getUserTaskIdentityCacheService().synchronizeIdentities();

    // then
    final IdentitySearchResultResponseDto allEntries = getUserTaskIdentityCacheService().searchIdentities("", 10);
    assertThat(allEntries.getTotal()).isEqualTo(2);
    assertThat(allEntries.getResult())
      .extracting(IdentityWithMetadataResponseDto::toIdentityDto)
      .extracting(IdentityDto::getId)
      .containsExactlyInAnyOrder(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_ID_CREW_MEMBERS);
  }

  @Test
  public void groupNotAssignedIsNotPresentAfterSync() {
    // given
    // groups are added via #beforeEach

    // when
    getUserTaskIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserTaskIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS)).isNotPresent();
  }

  @Test
  public void candidateGroupIsPresentAfterImport() {
    // given
    getUserTaskIdentityCacheService().resetCache();

    // when
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // then
    assertThat(getUserTaskIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS))
      .isPresent()
      .get()
      .extracting(GroupDto::getName)
      .isEqualTo(CANDIDATE_GROUP_NAME_CREW_MEMBERS);
  }

  @Test
  public void candidateGroupIsNotFetchedOnImportIfAlreadyPresentInCache() {
    // given
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // when
    final ClientAndServer firstEngineMockServer =
      useAndGetMockServerForEngine(engineIntegrationExtension.getEngineName());
    final HttpRequest getGroupRequest = request().withPath(".*/group/.*").withMethod(GET);

    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // then
    firstEngineMockServer.verify(getGroupRequest, VerificationTimes.exactly(0));
    assertThat(getUserTaskIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS)).isPresent();
  }

  @Test
  public void importCompletesEvenIfGettingGroupFails() {
    // given
    getUserTaskIdentityCacheService().resetCache();
    final ClientAndServer firstEngineMockServer =
      useAndGetMockServerForEngine(engineIntegrationExtension.getEngineName());

    final HttpRequest getGroupRequest = request().withPath(".*/group/.*").withMethod(GET);
    firstEngineMockServer.when(getGroupRequest).error(HttpError.error().withDropConnection(true));

    // when
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // then
    assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isNotPresent();
    firstEngineMockServer.verify(getGroupRequest);
    assigneeCandidateCacheServiceLogger.assertContains(
      "Failed to resolve and add assignee/candidateGroup identities from engine camunda-bpm"
    );
  }

  @Test
  public void assigneeAndCandidateGroupArePresentAfterSync() {
    // given
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);
    getUserTaskIdentityCacheService().resetCache();

    // when
    getUserTaskIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
      .isPresent()
      .get()
      .extracting(UserDto::getFirstName)
      .isEqualTo(JOHN_FIRST_NAME);
    assertThat(getUserTaskIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS))
      .isPresent()
      .get()
      .extracting(GroupDto::getName)
      .isEqualTo(CANDIDATE_GROUP_NAME_CREW_MEMBERS);
  }

  @Test
  public void assigneeAndCandidateGroupArePresentAfterImport() {
    // given
    // when
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // then
    assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
      .isPresent()
      .get()
      .extracting(UserDto::getFirstName)
      .isEqualTo(JOHN_FIRST_NAME);
    assertThat(getUserTaskIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS))
      .isPresent()
      .get()
      .extracting(GroupDto::getName)
      .isEqualTo(CANDIDATE_GROUP_NAME_CREW_MEMBERS);
  }

  private UserTaskIdentityCacheService getUserTaskIdentityCacheService() {
    return embeddedOptimizeExtension.getUserTaskIdentityCacheService();
  }

  private UserTaskIdentityCacheConfiguration getUserTaskIdentityCacheConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getUserTaskIdentityCacheConfiguration();
  }

  private void startSimpleUserTaskProcessWithAssigneeAndImport(final String assignee) {
    engineIntegrationExtension.deployAndStartProcess(BpmnModels.getUserTaskDiagramWithAssignee(assignee));
    importAllEngineEntitiesFromScratch();
  }

  private void startSimpleUserTaskProcessWithCandidateGroupAndImport(final String candidateGroup) {
    engineIntegrationExtension.deployAndStartProcess(BpmnModels.getUserTaskDiagramWithCandidateGroup(candidateGroup));
    importAllEngineEntitiesFromScratch();
  }
}
