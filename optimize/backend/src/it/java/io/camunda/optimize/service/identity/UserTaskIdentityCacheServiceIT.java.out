/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.identity;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static
// io.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_EMAIL_DOMAIN;
// import static jakarta.ws.rs.HttpMethod.GET;
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.assertj.core.api.Assertions.assertThatThrownBy;
// import static org.mockserver.model.HttpRequest.request;
//
// import com.google.common.collect.ImmutableList;
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.optimize.GroupDto;
// import io.camunda.optimize.dto.optimize.IdentityDto;
// import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
// import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
// import io.camunda.optimize.dto.optimize.UserDto;
// import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
// import io.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.exceptions.MaxEntryLimitHitException;
// import io.camunda.optimize.service.importing.job.IdentityLinkLogImportJob;
// import io.camunda.optimize.service.util.configuration.engine.UserTaskIdentityCacheConfiguration;
// import io.camunda.optimize.util.BpmnModels;
// import io.camunda.optimize.util.SuppressionConstants;
// import io.github.netmikey.logunit.api.LogCapturer;
// import java.util.List;
// import org.junit.jupiter.api.AfterEach;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.RegisterExtension;
// import org.mockserver.integration.ClientAndServer;
// import org.mockserver.model.HttpError;
// import org.mockserver.model.HttpRequest;
// import org.mockserver.verify.VerificationTimes;
//
// @Tag(OPENSEARCH_PASSING)
// public class UserTaskIdentityCacheServiceIT extends AbstractPlatformIT {
//   private static final String ASSIGNEE_ID_JOHN = "john";
//   private static final String JOHN_FIRST_NAME = "The";
//   private static final String JOHN_LAST_NAME = "Imposter";
//
//   private static final String ASSIGNEE_ID_JEAN = "jean";
//   private static final String JEAN_FIRST_NAME = "True";
//   private static final String JEAN_LAST_NAME = "CrewMember";
//
//   private static final String CANDIDATE_GROUP_ID_IMPOSTERS = "imposters";
//   private static final String CANDIDATE_GROUP_NAME_IMPOSTERS = "The Evil Imposters";
//
//   private static final String CANDIDATE_GROUP_ID_CREW_MEMBERS = "crewMembers";
//   private static final String CANDIDATE_GROUP_NAME_CREW_MEMBERS = "The Crew Members";
//
//   @RegisterExtension
//   protected final LogCapturer assigneeCandidateCacheServiceLogger =
//       LogCapturer.create().captureForType(PlatformUserTaskIdentityCache.class);
//
//   @RegisterExtension
//   protected final LogCapturer identityLinkLogImportJobLog =
//       LogCapturer.create().captureForType(IdentityLinkLogImportJob.class);
//
//   @BeforeEach
//   public void beforeEach() {
//     engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
//     engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
//     engineIntegrationExtension.createGroup(
//         CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
//     engineIntegrationExtension.createGroup(
//         CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
//     getUserTaskIdentityCacheService().stopScheduledSync();
//     getUserTaskIdentityCacheService().resetCache();
//   }
//
//   @AfterEach
//   public void afterEach() {
//     getUserTaskIdentityCacheService().startScheduledSync();
//   }
//
//   @Test
//   public void syncReplacesCache() {
//     // given
//     getUserTaskIdentityCacheService().synchronizeIdentities();
//
// assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isEmpty();
//
//     // when
//     startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
//     getUserTaskIdentityCacheService().synchronizeIdentities();
//
//     // then
//
// assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isPresent();
//   }
//
//   @Test
//   public void syncFailsOnCacheLimitHit() {
//     // given
//     startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
//     getUserTaskIdentityCacheService().synchronizeIdentities();
//
//     // when
//     // we have at least two users, but limit is now 1
//     getUserTaskIdentityCacheConfiguration().setMaxEntryLimit(1L);
//     startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);
//
//     final PlatformUserTaskIdentityCache assigneeIdentityCacheService =
//         getUserTaskIdentityCacheService();
//     assertThatThrownBy(assigneeIdentityCacheService::synchronizeIdentities)
//         // then
//         .isInstanceOf(MaxEntryLimitHitException.class);
//     assertThat(assigneeIdentityCacheService.getUserIdentityById(ASSIGNEE_ID_JOHN)).isPresent();
//
// assertThat(assigneeIdentityCacheService.getUserIdentityById(ASSIGNEE_ID_JEAN)).isNotPresent();
//   }
//
//   @Test
//   public void importDoesNotFailOnCacheLimitHit() {
//     // given
//     startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
//     getUserTaskIdentityCacheService().synchronizeIdentities();
//
//     // when
//     // we have at least two users, but limit is now 1
//     getUserTaskIdentityCacheConfiguration().setMaxEntryLimit(1L);
//     startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);
//
//     // then
//     identityLinkLogImportJobLog.assertContains(
//         "Failed forwarding identities to assignee & candidate group service");
//
// assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isPresent();
//     assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JEAN))
//         .isNotPresent();
//
//     final List<ProcessInstanceDto> processInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//     assertThat(processInstances)
//         .hasSize(2)
//         .flatExtracting(ProcessInstanceDto::getUserTasks)
//         .extracting(FlowNodeInstanceDto::getAssignee)
//         .containsExactlyInAnyOrder(ASSIGNEE_ID_JOHN, ASSIGNEE_ID_JEAN);
//   }
//
//   @Test
//   @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
//   public void assigneeIsPresentWithMetadataAfterSync() {
//     // given
//     startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
//     getUserTaskIdentityCacheService().resetCache();
//
//     // when
//     getUserTaskIdentityCacheService().synchronizeIdentities();
//
//     // then
//     assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
//         .isPresent()
//         .get()
//         .extracting(UserDto::getFirstName, UserDto::getLastName, UserDto::getEmail)
//         .containsExactly(JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN +
// DEFAULT_EMAIL_DOMAIN);
//   }
//
//   @Test
//   @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
//   public void assigneeIsPresentWithoutMetadataAfterSyncIfMetadataDisabled() {
//     // given
//     getUserTaskIdentityCacheService().resetCache();
//     startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
//     getUserTaskIdentityCacheConfiguration().setIncludeUserMetaData(false);
//
//     // when
//     getUserTaskIdentityCacheService().synchronizeIdentities();
//
//     // then
//     assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
//         .isPresent()
//         .get()
//         .extracting(UserDto::getFirstName, UserDto::getLastName, UserDto::getEmail)
//         .containsExactly(null, null, null);
//   }
//
//   @Test
//   public void twoAssigneesFromSameProcessInstanceArePresentAfterSync() {
//     // given
//     final ProcessInstanceEngineDto processInstanceEngineDto =
//         engineIntegrationExtension.deployAndStartProcess(
//             BpmnModels.getDoubleUserTaskDiagramWithAssignees(ASSIGNEE_ID_JOHN,
// ASSIGNEE_ID_JEAN));
//     engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceEngineDto.getId());
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     getUserTaskIdentityCacheService().synchronizeIdentities();
//
//     // then
//     final IdentitySearchResultResponseDto allEntries =
//         getUserTaskIdentityCacheService().searchIdentities("", 10);
//     assertThat(allEntries.getResult())
//         .extracting(IdentityWithMetadataResponseDto::toIdentityDto)
//         .extracting(IdentityDto::getId)
//         .containsExactlyInAnyOrder(ASSIGNEE_ID_JOHN, ASSIGNEE_ID_JEAN);
//   }
//
//   @Test
//   public void userNotPresentAsAssigneeIsNotPresentAfterSync() {
//     // given
//     // users are added via #beforeEach
//
//     // when
//     getUserTaskIdentityCacheService().synchronizeIdentities();
//
//     // then
//     assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
//         .isNotPresent();
//   }
//
//   @Test
//   public void assigneeNotInEngineIsPresentAfterSync() {
//     // given
//     getUserTaskIdentityCacheService().resetCache();
//     final String userId = "aUserId";
//
//     // when
//     startSimpleUserTaskProcessWithAssigneeAndImport(userId);
//
//     // then
//     assertThat(getUserTaskIdentityCacheService().getUserIdentityById(userId))
//         .isPresent()
//         .get()
//         .extracting(UserDto::getId, UserDto::getFirstName, UserDto::getLastName,
// UserDto::getEmail)
//         .containsExactly(userId, null, null, null);
//   }
//
//   @Test
//   @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
//   public void assigneeIsPresentWithMetadataAfterImport() {
//     // given
//     getUserTaskIdentityCacheService().resetCache();
//
//     // when
//     startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
//
//     // then
//     assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
//         .isPresent()
//         .get()
//         .extracting(UserDto::getFirstName, UserDto::getLastName, UserDto::getEmail)
//         .containsExactly(JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN +
// DEFAULT_EMAIL_DOMAIN);
//   }
//
//   @Test
//   @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
//   public void assigneeIsPresentWithoutMetadataAfterImportIfMetadataDisabled() {
//     // given
//     getUserTaskIdentityCacheService().resetCache();
//     getUserTaskIdentityCacheConfiguration().setIncludeUserMetaData(false);
//
//     // when
//     startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
//
//     // then
//     assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
//         .isPresent()
//         .get()
//         .extracting(UserDto::getFirstName, UserDto::getLastName, UserDto::getEmail)
//         .containsExactly(null, null, null);
//   }
//
//   @Test
//   public void assigneeUserIsNotFetchedOnImportIfAlreadyPresentInCache() {
//     // given
//     startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
//
//     // when
//     final ClientAndServer firstEngineMockServer =
//         useAndGetMockServerForEngine(engineIntegrationExtension.getEngineName());
//     final HttpRequest getUserRequest = request().withPath(".*/user/.*").withMethod(GET);
//
//     startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
//
//     // then
//     firstEngineMockServer.verify(getUserRequest, VerificationTimes.exactly(0));
//
// assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isPresent();
//   }
//
//   @Test
//   public void importCompletesEvenIfGettingAssigneesFails() {
//     // given
//     getUserTaskIdentityCacheService().resetCache();
//     final ClientAndServer firstEngineMockServer =
//         useAndGetMockServerForEngine(engineIntegrationExtension.getEngineName());
//
//     final HttpRequest getUserRequest = request().withPath(".*/user/.*").withMethod(GET);
//     firstEngineMockServer.when(getUserRequest).error(HttpError.error().withDropConnection(true));
//
//     // when
//     startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
//
//     // then
//     assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
//         .isNotPresent();
//     firstEngineMockServer.verify(getUserRequest);
//     assigneeCandidateCacheServiceLogger.assertContains(
//         "Failed to resolve and add platform assignee/candidateGroup identities from engine
// camunda-bpm");
//   }
//
//   @Test
//   public void candidateGroupIsPresentAfterSync() {
//     // given
//     startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);
//
//     // when
//     getUserTaskIdentityCacheService().synchronizeIdentities();
//
//     // then
//     assertThat(
//
// getUserTaskIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS))
//         .isPresent()
//         .get()
//         .extracting(GroupDto::getName)
//         .isEqualTo(CANDIDATE_GROUP_NAME_CREW_MEMBERS);
//   }
//
//   @Test
//   public void candidateGroupNotInEngineIsPresentAfterSync() {
//     // given
//     getUserTaskIdentityCacheService().resetCache();
//     final String groupId = "aGroupId";
//
//     // when
//     startSimpleUserTaskProcessWithCandidateGroupAndImport(groupId);
//
//     // then
//     assertThat(getUserTaskIdentityCacheService().getGroupIdentityById(groupId))
//         .isPresent()
//         .get()
//         .extracting(GroupDto::getId, GroupDto::getName)
//         .containsExactly(groupId, groupId);
//   }
//
//   @Test
//   public void twoCandidateGroupsFromSameProcessInstanceArePresentAfterSync() {
//     // given
//     engineIntegrationExtension.deployAndStartProcess(
//         BpmnModels.getUserTaskDiagramWithMultipleCandidateGroups(
//             ImmutableList.of(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_ID_CREW_MEMBERS)));
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     getUserTaskIdentityCacheService().synchronizeIdentities();
//
//     // then
//     final IdentitySearchResultResponseDto allEntries =
//         getUserTaskIdentityCacheService().searchIdentities("", 10);
//     assertThat(allEntries.getResult())
//         .extracting(IdentityWithMetadataResponseDto::toIdentityDto)
//         .extracting(IdentityDto::getId)
//         .containsExactlyInAnyOrder(CANDIDATE_GROUP_ID_IMPOSTERS,
// CANDIDATE_GROUP_ID_CREW_MEMBERS);
//   }
//
//   @Test
//   public void groupNotAssignedIsNotPresentAfterSync() {
//     // given
//     // groups are added via #beforeEach
//
//     // when
//     getUserTaskIdentityCacheService().synchronizeIdentities();
//
//     // then
//     assertThat(
//
// getUserTaskIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS))
//         .isNotPresent();
//   }
//
//   @Test
//   public void candidateGroupIsPresentAfterImport() {
//     // given
//     getUserTaskIdentityCacheService().resetCache();
//
//     // when
//     startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);
//
//     // then
//     assertThat(
//
// getUserTaskIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS))
//         .isPresent()
//         .get()
//         .extracting(GroupDto::getName)
//         .isEqualTo(CANDIDATE_GROUP_NAME_CREW_MEMBERS);
//   }
//
//   @Test
//   public void candidateGroupIsNotFetchedOnImportIfAlreadyPresentInCache() {
//     // given
//     startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);
//
//     // when
//     final ClientAndServer firstEngineMockServer =
//         useAndGetMockServerForEngine(engineIntegrationExtension.getEngineName());
//     final HttpRequest getGroupRequest = request().withPath(".*/group/.*").withMethod(GET);
//
//     startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);
//
//     // then
//     firstEngineMockServer.verify(getGroupRequest, VerificationTimes.exactly(0));
//     assertThat(
//
// getUserTaskIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS))
//         .isPresent();
//   }
//
//   @Test
//   public void importCompletesEvenIfGettingGroupFails() {
//     // given
//     getUserTaskIdentityCacheService().resetCache();
//     final ClientAndServer firstEngineMockServer =
//         useAndGetMockServerForEngine(engineIntegrationExtension.getEngineName());
//
//     final HttpRequest getGroupRequest = request().withPath(".*/group/.*").withMethod(GET);
//
// firstEngineMockServer.when(getGroupRequest).error(HttpError.error().withDropConnection(true));
//
//     // when
//     startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);
//
//     // then
//     assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
//         .isNotPresent();
//     firstEngineMockServer.verify(getGroupRequest);
//     assigneeCandidateCacheServiceLogger.assertContains(
//         "Failed to resolve and add platform assignee/candidateGroup identities from engine
// camunda-bpm");
//   }
//
//   @Test
//   public void assigneeAndCandidateGroupArePresentAfterSync() {
//     // given
//     startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
//     startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);
//     getUserTaskIdentityCacheService().resetCache();
//
//     // when
//     getUserTaskIdentityCacheService().synchronizeIdentities();
//
//     // then
//     assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
//         .isPresent()
//         .get()
//         .extracting(UserDto::getFirstName)
//         .isEqualTo(JOHN_FIRST_NAME);
//     assertThat(
//
// getUserTaskIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS))
//         .isPresent()
//         .get()
//         .extracting(GroupDto::getName)
//         .isEqualTo(CANDIDATE_GROUP_NAME_CREW_MEMBERS);
//   }
//
//   @Test
//   public void assigneeAndCandidateGroupArePresentAfterImport() {
//     // given
//     // when
//     startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
//     startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);
//
//     // then
//     assertThat(getUserTaskIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
//         .isPresent()
//         .get()
//         .extracting(UserDto::getFirstName)
//         .isEqualTo(JOHN_FIRST_NAME);
//     assertThat(
//
// getUserTaskIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS))
//         .isPresent()
//         .get()
//         .extracting(GroupDto::getName)
//         .isEqualTo(CANDIDATE_GROUP_NAME_CREW_MEMBERS);
//   }
//
//   private PlatformUserTaskIdentityCache getUserTaskIdentityCacheService() {
//     return embeddedOptimizeExtension.getUserTaskIdentityCache();
//   }
//
//   private UserTaskIdentityCacheConfiguration getUserTaskIdentityCacheConfiguration() {
//     return embeddedOptimizeExtension
//         .getConfigurationService()
//         .getUserTaskIdentityCacheConfiguration();
//   }
//
//   private void startSimpleUserTaskProcessWithAssigneeAndImport(final String assignee) {
//     engineIntegrationExtension.deployAndStartProcess(
//         BpmnModels.getUserTaskDiagramWithAssignee(assignee));
//     importAllEngineEntitiesFromScratch();
//   }
//
//   private void startSimpleUserTaskProcessWithCandidateGroupAndImport(final String candidateGroup)
// {
//     engineIntegrationExtension.deployAndStartProcess(
//         BpmnModels.getUserTaskDiagramWithCandidateGroup(candidateGroup));
//     importAllEngineEntitiesFromScratch();
//   }
// }
