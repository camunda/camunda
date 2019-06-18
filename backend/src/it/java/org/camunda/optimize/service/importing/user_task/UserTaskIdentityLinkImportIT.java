/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.user_task;

import com.google.common.collect.ImmutableSet;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.UserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import org.camunda.optimize.dto.optimize.persistence.CandidateGroupOperationDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.IDENTITY_LINK_OPERATION_ADD;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.IDENTITY_LINK_OPERATION_DELETE;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;


public class UserTaskIdentityLinkImportIT extends AbstractUserTaskImportIT {

  @Test
  public void identityLinksLogsAreImported() throws Exception {
    // given
    deployAndStartOneUserTaskProcess();
    String defaultCandidateGroup = "defaultCandidateGroupId";
    engineRule.createGroup(defaultCandidateGroup);
    engineRule.addCandidateGroupForAllRunningUserTasks(defaultCandidateGroup);
    engineRule.finishAllRunningUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      persistedProcessInstanceDto.getUserTasks()
        .forEach(userTask -> {
          assertThat(userTask.getAssignee(), is(DEFAULT_USERNAME));
          assertThat(userTask.getCandidateGroups(), contains(defaultCandidateGroup));
          assertThat(userTask.getAssigneeOperations().size(), is(1));
          userTask.getAssigneeOperations().forEach(assigneeOperationDto -> {
            assertThat(assigneeOperationDto.getId(), is(notNullValue()));
            assertThat(assigneeOperationDto.getUserId(), is(DEFAULT_USERNAME));
            assertThat(assigneeOperationDto.getTimestamp(), is(notNullValue()));
            assertThat(assigneeOperationDto.getOperationType(), is(IDENTITY_LINK_OPERATION_ADD));
          });
          assertThat(userTask.getCandidateGroupOperations().size(), is(1));
          userTask.getCandidateGroupOperations().forEach(candidateGroupOperationDto -> {
            assertThat(candidateGroupOperationDto.getId(), is(notNullValue()));
            assertThat(candidateGroupOperationDto.getGroupId(), is(defaultCandidateGroup));
            assertThat(candidateGroupOperationDto.getTimestamp(), is(notNullValue()));
            assertThat(candidateGroupOperationDto.getOperationType(), is(IDENTITY_LINK_OPERATION_ADD));
          });
        });
    }
  }

  @Test
  public void assigneeIsCorrectlyDeterminedForMultipleUserTasks() throws Exception {
    // given
    deployAndStartTwoUserTasksProcess();
    engineRule.addUser("secondUser", "fooPassword");
    engineRule.grantAllAuthorizations("secondUser");
    engineRule.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    engineRule.finishAllRunningUserTasks("secondUser", "fooPassword");

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      List<UserTaskInstanceDto> userTasks = processInstanceDto.getUserTasks();
      assertThat(userTasks.size(), is(2));
      Set<String> expectedAssignees = ImmutableSet.of(DEFAULT_USERNAME, "secondUser");
      Set<String> actualAssignees = userTasks.stream()
        .map(UserTaskInstanceDto::getAssignee)
        .collect(Collectors.toSet());
      assertThat(actualAssignees, is(expectedAssignees));
    }
  }

  @Test
  public void candidateGroupIsCorrectlyDeterminedForMultipleUserTasks() throws Exception {
    // given
    deployAndStartTwoUserTasksProcess();
    engineRule.createGroup("firstGroup");
    engineRule.addCandidateGroupForAllRunningUserTasks("firstGroup");
    engineRule.finishAllRunningUserTasks();
    engineRule.createGroup("secondGroup");
    engineRule.addCandidateGroupForAllRunningUserTasks("secondGroup");
    engineRule.finishAllRunningUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      List<UserTaskInstanceDto> userTasks = processInstanceDto.getUserTasks();
      assertThat(userTasks.size(), is(2));
      Set<String> expectedCandidateGroups = ImmutableSet.of("firstGroup", "secondGroup");
      Set<String> actualCandidateGroups = userTasks.stream()
        .map(userTask -> {
          assertThat(userTask.getCandidateGroups().size(), is(1));
          return userTask.getCandidateGroups().get(0);
        })
        .collect(Collectors.toSet());
      assertThat(actualCandidateGroups, is(expectedCandidateGroups));
    }
  }

  @Test
  public void severalAssigneeOperationsLeadToCorrectResult() throws Exception {
    // given
    deployAndStartOneUserTaskProcess();
    engineRule.claimAllRunningUserTasks();
    engineRule.unclaimAllRunningUserTasks();
    engineRule.addUser("secondUser", "secondPassword");
    engineRule.grantAllAuthorizations("secondUser");
    engineRule.finishAllRunningUserTasks("secondUser", "secondPassword");

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      List<UserTaskInstanceDto> userTasks = processInstanceDto.getUserTasks();
      assertThat(userTasks.size(), is(1));
      List<AssigneeOperationDto> assigneeOperations = userTasks.get(0).getAssigneeOperations();
      assertThat(assigneeOperations.get(0).getOperationType(), is(IDENTITY_LINK_OPERATION_ADD));
      assertThat(assigneeOperations.get(1).getOperationType(), is(IDENTITY_LINK_OPERATION_DELETE));
      assertThat(assigneeOperations.get(2).getOperationType(), is(IDENTITY_LINK_OPERATION_ADD));
      assertThat(userTasks.get(0).getAssignee(), is("secondUser"));
    }
  }

  @Test
  public void assigneeWithoutClaimIsNull() throws Exception {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartOneUserTaskProcess();
    engineRule.completeUserTaskWithoutClaim(engineDto.getId());

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      List<UserTaskInstanceDto> userTasks = processInstanceDto.getUserTasks();
      assertThat(userTasks.size(), is(1));
      assertThat(userTasks.get(0).getAssignee(), nullValue());
    }
  }

  @Test
  public void assigneeCanBeDeterminedForStillRunningUserTasks() throws Exception {
    // given
    deployAndStartOneUserTaskProcess();
    engineRule.claimAllRunningUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      List<UserTaskInstanceDto> userTasks = processInstanceDto.getUserTasks();
      assertThat(userTasks.size(), is(1));
      assertThat(userTasks.get(0).getAssignee(), is(DEFAULT_USERNAME));
    }
  }

  @Test
  public void severalCandidateGroupOperationsLeadToCorrectResult() throws Exception {
    // given
    deployAndStartOneUserTaskProcess();
    engineRule.createGroup("firstGroup");
    engineRule.createGroup("secondGroup");
    engineRule.addCandidateGroupForAllRunningUserTasks("firstGroup");
    engineRule.addCandidateGroupForAllRunningUserTasks("secondGroup");
    engineRule.deleteCandidateGroupForAllRunningUserTasks("firstGroup");
    engineRule.finishAllRunningUserTasks();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      List<UserTaskInstanceDto> userTasks = processInstanceDto.getUserTasks();
      assertThat(userTasks.size(), is(1));
      List<CandidateGroupOperationDto> candidateGroupOperations = userTasks.get(0).getCandidateGroupOperations();
      assertThat(candidateGroupOperations.get(0).getOperationType(), is(IDENTITY_LINK_OPERATION_ADD));
      assertThat(candidateGroupOperations.get(1).getOperationType(), is(IDENTITY_LINK_OPERATION_ADD));
      assertThat(candidateGroupOperations.get(2).getOperationType(), is(IDENTITY_LINK_OPERATION_DELETE));
      assertThat(userTasks.get(0).getCandidateGroups(), contains("secondGroup"));
    }
  }

  @Test
  public void deleteAssigneeAndDeleteCandidateGroupAsLastOperations() throws Exception {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartOneUserTaskProcess();
    engineRule.createGroup("firstGroup");
    engineRule.addCandidateGroupForAllRunningUserTasks("firstGroup");
    engineRule.deleteCandidateGroupForAllRunningUserTasks("firstGroup");
    engineRule.claimAllRunningUserTasks();
    engineRule.unclaimAllRunningUserTasks();
    engineRule.completeUserTaskWithoutClaim(engineDto.getId());

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      List<UserTaskInstanceDto> userTasks = processInstanceDto.getUserTasks();
      assertThat(userTasks.size(), is(1));
      assertThat(userTasks.get(0).getAssignee(), nullValue());
      assertThat(userTasks.get(0).getCandidateGroups().size(), is(0));
    }
  }

  @Test
  public void importIsNotAffectedByPagination() throws Exception {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartOneUserTaskProcess();
    engineRule.claimAllRunningUserTasks();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    engineRule.unclaimAllRunningUserTasks();
    engineRule.addUser("secondUser", "aPassword");
    engineRule.grantAllAuthorizations("secondUser");
    engineRule.finishAllRunningUserTasks("secondUser", "aPassword");
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      List<UserTaskInstanceDto> userTasks = processInstanceDto.getUserTasks();
      assertThat(userTasks.size(), is(1));
      assertThat(userTasks.get(0).getAssignee(), is("secondUser"));
    }
  }

}
