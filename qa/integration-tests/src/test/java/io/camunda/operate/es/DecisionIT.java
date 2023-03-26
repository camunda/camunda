/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.webapp.rest.DecisionRestService;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionGroupDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests Elasticsearch queries for decision.
 */
public class DecisionIT extends OperateIntegrationTest {

  private static final String QUERY_DECISION_GROUPED_URL = DecisionRestService.DECISION_URL + "/grouped";

  @MockBean
  private PermissionsService permissionsService;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Test
  public void testDecisionsGroupedWithPermisssionWhenNotAllowed() throws Exception {
    // given
    String id1 = "111";
    String id2 = "222";
    String id3 = "333";
    String decisionId1 = "decisionId1";
    String decisionId2 = "decisionId2";
    String decisionId3 = "decisionId3";

    final DecisionDefinitionEntity decision1 = new DecisionDefinitionEntity().setId(id1).setDecisionId(decisionId1);
    final DecisionDefinitionEntity decision2 = new DecisionDefinitionEntity().setId(id2).setDecisionId(decisionId2);
    final DecisionDefinitionEntity decision3 = new DecisionDefinitionEntity().setId(id3).setDecisionId(decisionId3);
    elasticsearchTestRule.persistNew(decision1, decision2, decision3);

    // when
    when(permissionsService.getDecisionsWithPermission(IdentityPermission.READ)).thenReturn(
        PermissionsService.ResourcesAllowed.withIds(Set.of()));
    when(permissionsService.createQueryForDecisionsByPermission(IdentityPermission.READ)).thenCallRealMethod();
    MvcResult mvcResult = getRequest(QUERY_DECISION_GROUPED_URL);

    // then
    List<DecisionGroupDto> response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response).isEmpty();
  }

  @Test
  public void testDecisionsGroupedWithPermisssionWhenAllowed() throws Exception {
    // given
    String id1 = "111";
    String id2 = "222";
    String id3 = "333";
    String decisionId1 = "decisionId1";
    String decisionId2 = "decisionId2";
    String decisionId3 = "decisionId3";

    final DecisionDefinitionEntity decision1 = new DecisionDefinitionEntity().setId(id1).setDecisionId(decisionId1);
    final DecisionDefinitionEntity decision2 = new DecisionDefinitionEntity().setId(id2).setDecisionId(decisionId2);
    final DecisionDefinitionEntity decision3 = new DecisionDefinitionEntity().setId(id3).setDecisionId(decisionId3);
    elasticsearchTestRule.persistNew(decision1, decision2, decision3);

    // when
    when(permissionsService.getDecisionsWithPermission(IdentityPermission.READ)).thenReturn(
        PermissionsService.ResourcesAllowed.all());
    when(permissionsService.createQueryForDecisionsByPermission(IdentityPermission.READ)).thenCallRealMethod();
    MvcResult mvcResult = getRequest(QUERY_DECISION_GROUPED_URL);

    // then
    List<DecisionGroupDto> response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response).hasSize(3);
    assertThat(response.stream().map(DecisionGroupDto::getDecisionId).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(decisionId1, decisionId2, decisionId3);
  }

  @Test
  public void testDecisionsGroupedWithPermisssionWhenSomeAllowed() throws Exception {
    // given
    String id1 = "111";
    String id2 = "222";
    String id3 = "333";
    String decisionId1 = "decisionId1";
    String decisionId2 = "decisionId2";
    String decisionId3 = "decisionId3";

    final DecisionDefinitionEntity decision1 = new DecisionDefinitionEntity().setId(id1).setDecisionId(decisionId1);
    final DecisionDefinitionEntity decision2 = new DecisionDefinitionEntity().setId(id2).setDecisionId(decisionId2);
    final DecisionDefinitionEntity decision3 = new DecisionDefinitionEntity().setId(id3).setDecisionId(decisionId3);
    elasticsearchTestRule.persistNew(decision1, decision2, decision3);

    // when
    when(permissionsService.getDecisionsWithPermission(IdentityPermission.READ)).thenReturn(
        PermissionsService.ResourcesAllowed.withIds(Set.of(decisionId2)));
    when(permissionsService.createQueryForDecisionsByPermission(IdentityPermission.READ)).thenCallRealMethod();
    MvcResult mvcResult = getRequest(QUERY_DECISION_GROUPED_URL);

    // then
    List<DecisionGroupDto> response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    assertThat(response).hasSize(1);
    assertThat(response.stream().map(DecisionGroupDto::getDecisionId).collect(Collectors.toList()))
        .containsExactly(decisionId2);
  }
}
