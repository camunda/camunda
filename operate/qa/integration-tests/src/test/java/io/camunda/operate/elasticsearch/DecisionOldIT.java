/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
    String id1 = "111";
    String id2 = "222";
    String id3 = "333";
    String decisionId1 = "decisionId1";
    String decisionId2 = "decisionId2";
    String decisionId3 = "decisionId3";

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
    MvcResult mvcResult = getRequest(QUERY_DECISION_GROUPED_URL);

    // then
    List<DecisionGroupDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

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
    MvcResult mvcResult = getRequest(QUERY_DECISION_GROUPED_URL);

    // then
    List<DecisionGroupDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

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
    MvcResult mvcResult = getRequest(QUERY_DECISION_GROUPED_URL);

    // then
    List<DecisionGroupDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(1);
    assertThat(response.stream().map(DecisionGroupDto::getDecisionId).collect(Collectors.toList()))
        .containsExactly(decisionId2);
  }
}
