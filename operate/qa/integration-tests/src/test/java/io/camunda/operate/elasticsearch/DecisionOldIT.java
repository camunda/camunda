/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.webapp.rest.DecisionRestService;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionGroupDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

/** Tests Elasticsearch queries for decision. */
public class DecisionOldIT extends OperateAbstractIT {

  private static final String QUERY_DECISION_GROUPED_URL =
      DecisionRestService.DECISION_URL + "/grouped";
  @Rule public SearchTestRule elasticsearchTestRule = new SearchTestRule();
  @MockBean private PermissionsService permissionsService;

  @Test
  public void testDecisionsGroupedWithPermisssionWhenNotAllowed() throws Exception {
    // given
    final String id1 = "111";
    final String id2 = "222";
    final String id3 = "333";
    final String decisionId1 = "decisionId1";
    final String decisionId2 = "decisionId2";
    final String decisionId3 = "decisionId3";

    final DecisionDefinitionEntity decision1 =
        new DecisionDefinitionEntity().setId(id1).setDecisionId(decisionId1);
    final DecisionDefinitionEntity decision2 =
        new DecisionDefinitionEntity().setId(id2).setDecisionId(decisionId2);
    final DecisionDefinitionEntity decision3 =
        new DecisionDefinitionEntity().setId(id3).setDecisionId(decisionId3);
    elasticsearchTestRule.persistNew(decision1, decision2, decision3);

    // when
    when(permissionsService.getDecisionsWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));
    final MvcResult mvcResult = getRequest(QUERY_DECISION_GROUPED_URL);

    // then
    final List<DecisionGroupDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).isEmpty();
  }

  @Test
  public void testDecisionsGroupedWithPermisssionWhenAllowed() throws Exception {
    // given
    final String id1 = "111";
    final String id2 = "222";
    final String id3 = "333";
    final String decisionId1 = "decisionId1";
    final String decisionId2 = "decisionId2";
    final String decisionId3 = "decisionId3";

    final DecisionDefinitionEntity decision1 =
        new DecisionDefinitionEntity().setId(id1).setDecisionId(decisionId1);
    final DecisionDefinitionEntity decision2 =
        new DecisionDefinitionEntity().setId(id2).setDecisionId(decisionId2);
    final DecisionDefinitionEntity decision3 =
        new DecisionDefinitionEntity().setId(id3).setDecisionId(decisionId3);
    elasticsearchTestRule.persistNew(decision1, decision2, decision3);

    // when
    when(permissionsService.getDecisionsWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.all());
    final MvcResult mvcResult = getRequest(QUERY_DECISION_GROUPED_URL);

    // then
    final List<DecisionGroupDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(3);
    assertThat(response.stream().map(DecisionGroupDto::getDecisionId).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(decisionId1, decisionId2, decisionId3);
  }

  @Test
  public void testDecisionsGroupedWithPermisssionWhenSomeAllowed() throws Exception {
    // given
    final String id1 = "111";
    final String id2 = "222";
    final String id3 = "333";
    final String decisionId1 = "decisionId1";
    final String decisionId2 = "decisionId2";
    final String decisionId3 = "decisionId3";

    final DecisionDefinitionEntity decision1 =
        new DecisionDefinitionEntity().setId(id1).setDecisionId(decisionId1);
    final DecisionDefinitionEntity decision2 =
        new DecisionDefinitionEntity().setId(id2).setDecisionId(decisionId2);
    final DecisionDefinitionEntity decision3 =
        new DecisionDefinitionEntity().setId(id3).setDecisionId(decisionId3);
    elasticsearchTestRule.persistNew(decision1, decision2, decision3);

    // when
    when(permissionsService.getDecisionsWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of(decisionId2)));
    final MvcResult mvcResult = getRequest(QUERY_DECISION_GROUPED_URL);

    // then
    final List<DecisionGroupDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(1);
    assertThat(response.stream().map(DecisionGroupDto::getDecisionId).collect(Collectors.toList()))
        .containsExactly(decisionId2);
  }
}
