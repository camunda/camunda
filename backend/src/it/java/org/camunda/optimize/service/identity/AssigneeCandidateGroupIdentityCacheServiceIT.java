/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.identity;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.MaxEntryLimitHitException;
import org.camunda.optimize.service.util.configuration.engine.IdentitySyncConfiguration;
import org.camunda.optimize.util.BpmnModels;
import org.camunda.optimize.util.SuppressionConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_EMAIL_DOMAIN;

public class AssigneeCandidateGroupIdentityCacheServiceIT extends AbstractIT {
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
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JOHN, JOHN_FIRST_NAME, JOHN_LAST_NAME);
    engineIntegrationExtension.addUser(ASSIGNEE_ID_JEAN, JEAN_FIRST_NAME, JEAN_LAST_NAME);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_NAME_IMPOSTERS);
    engineIntegrationExtension.createGroup(CANDIDATE_GROUP_ID_CREW_MEMBERS, CANDIDATE_GROUP_NAME_CREW_MEMBERS);
  }

  @Test
  public void verifySyncEnabledByDefault() {
    assertThat(getIdentityCacheService().isScheduledToRun()).isTrue();
  }

  @Test
  public void testSyncStoppedSuccessfully() {
    try {
      getIdentityCacheService().stopScheduledSync();
      assertThat(getIdentityCacheService().isScheduledToRun()).isFalse();
    } finally {
      getIdentityCacheService().startScheduledSync();
    }
  }

  @Test
  public void testCacheReplacedOnNewSync() {
    try {
      // given
      getIdentityCacheService().stopScheduledSync();
      getIdentityCacheService().synchronizeIdentities();
      assertThat(getIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isEmpty();

      // when
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
      getIdentityCacheService().synchronizeIdentities();

      // then
      assertThat(getIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isPresent();
    } finally {
      getIdentityCacheService().startScheduledSync();
    }
  }

  @Test
  public void testCacheNotReplacedOnLimitHit() {
    try {
      // given
      getIdentityCacheService().stopScheduledSync();
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
      getIdentityCacheService().synchronizeIdentities();

      // when
      // we have at least two users, but limit is now 1
      startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JEAN);
      getIdentitySyncConfiguration().setMaxEntryLimit(1L);

      // then
      final AssigneeCandidateGroupIdentityCacheService userIdentityCacheService = getIdentityCacheService();
      assertThatThrownBy(userIdentityCacheService::synchronizeIdentities)
        .isInstanceOf(MaxEntryLimitHitException.class);
      assertThat(getIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isPresent();
      assertThat(getIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JEAN)).isNotPresent();
    } finally {
      getIdentityCacheService().startScheduledSync();
    }
  }

  @Test
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void assigneeIsPresentWithMetadata() {
    // given
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);

    // when
    getIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
      .isPresent()
      .get()
      .extracting(UserDto::getFirstName, UserDto::getLastName, UserDto::getEmail)
      .containsExactly(JOHN_FIRST_NAME, JOHN_LAST_NAME, ASSIGNEE_ID_JOHN + DEFAULT_EMAIL_DOMAIN);
  }

  @Test
  public void twoAssigneesFromSameProcessInstanceArePresent() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = engineIntegrationExtension.deployAndStartProcess(
      BpmnModels.getDoubleUserTaskDiagramWithAssignees(ASSIGNEE_ID_JOHN, ASSIGNEE_ID_JEAN)
    );
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceEngineDto.getId());
    importAllEngineEntitiesFromScratch();

    // when
    getIdentityCacheService().synchronizeIdentities();

    // then
    final IdentitySearchResultResponseDto allEntries = getIdentityCacheService().searchIdentities("", 10);
    assertThat(allEntries.getTotal()).isEqualTo(2);
    assertThat(allEntries.getResult())
      .extracting(IdentityWithMetadataResponseDto::toIdentityDto)
      .extracting(IdentityDto::getId)
      .containsExactlyInAnyOrder(ASSIGNEE_ID_JOHN, ASSIGNEE_ID_JEAN);
  }

  @Test
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public void assigneeIsPresentMetadataDisabled() {
    // given
    startSimpleUserTaskProcessWithAssigneeAndImport(ASSIGNEE_ID_JOHN);
    getIdentitySyncConfiguration().setIncludeUserMetaData(false);

    // when
    getIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN))
      .isPresent()
      .get()
      .extracting(UserDto::getFirstName, UserDto::getLastName, UserDto::getEmail)
      .containsExactly(null, null, null);
  }

  @Test
  public void userNotPresentAsAssigneeIsNotImported() {
    // given
    // users are added via #beforeEach

    // when
    getIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getIdentityCacheService().getUserIdentityById(ASSIGNEE_ID_JOHN)).isNotPresent();
  }

  @Test
  public void candidateGroupIsPresent() {
    // given
    startSimpleUserTaskProcessWithCandidateGroupAndImport(CANDIDATE_GROUP_ID_CREW_MEMBERS);

    // when
    getIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS))
      .isPresent()
      .get()
      .extracting(GroupDto::getName)
      .isEqualTo(CANDIDATE_GROUP_NAME_CREW_MEMBERS);
  }

  @Test
  public void twoCandidateGroupsFromSameProcessInstanceArePresent() {
    // given
    engineIntegrationExtension.deployAndStartProcess(BpmnModels.getUserTaskDiagramWithMultipleCandidateGroups(
      ImmutableList.of(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_ID_CREW_MEMBERS)
    ));
    importAllEngineEntitiesFromScratch();

    // when
    getIdentityCacheService().synchronizeIdentities();

    // then
    final IdentitySearchResultResponseDto allEntries = getIdentityCacheService().searchIdentities("", 10);
    assertThat(allEntries.getTotal()).isEqualTo(2);
    assertThat(allEntries.getResult())
      .extracting(IdentityWithMetadataResponseDto::toIdentityDto)
      .extracting(IdentityDto::getId)
      .containsExactlyInAnyOrder(CANDIDATE_GROUP_ID_IMPOSTERS, CANDIDATE_GROUP_ID_CREW_MEMBERS);
  }

  @Test
  public void groupNotAssignedIsNotImported() {
    // given
    // groups are added via #beforeEach

    // when
    getIdentityCacheService().synchronizeIdentities();

    // then
    assertThat(getIdentityCacheService().getGroupIdentityById(CANDIDATE_GROUP_ID_CREW_MEMBERS)).isNotPresent();
  }

  private AssigneeCandidateGroupIdentityCacheService getIdentityCacheService() {
    return embeddedOptimizeExtension.getAssigneeCandidateGroupIdentityCacheService();
  }

  private IdentitySyncConfiguration getIdentitySyncConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getIdentitySyncConfiguration();
  }

  public void startSimpleUserTaskProcessWithAssigneeAndImport(final String assignee) {
    engineIntegrationExtension.deployAndStartProcess(BpmnModels.getUserTaskDiagramWithAssignee(assignee));
    importAllEngineEntitiesFromScratch();
  }

  public void startSimpleUserTaskProcessWithCandidateGroupAndImport(final String candidateGroup) {
    engineIntegrationExtension.deployAndStartProcess(BpmnModels.getUserTaskDiagramWithCandidateGroup(candidateGroup));
    importAllEngineEntitiesFromScratch();
  }
}
