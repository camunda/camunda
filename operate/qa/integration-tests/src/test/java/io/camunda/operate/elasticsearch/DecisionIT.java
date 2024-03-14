/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.webapp.rest.DecisionRestService;
import io.camunda.operate.webapp.rest.dto.DecisionRequestDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionGroupDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.camunda.operate.webapp.security.tenant.TenantService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Tests Elasticsearch queries for decision. */
@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = true"
    })
public class DecisionIT extends OperateAbstractIT {

  private static final String QUERY_DECISION_GROUPED_URL =
      DecisionRestService.DECISION_URL + "/grouped";
  private static final String QUERY_DECISION_XML_URL_PATTERN =
      DecisionRestService.DECISION_URL + "/%s/xml";
  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
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
    searchTestRule.persistNew(decision1, decision2, decision3);

    // when
    when(permissionsService.getDecisionsWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));
    MvcResult mvcResult = postRequest(QUERY_DECISION_GROUPED_URL, new DecisionRequestDto());

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
    searchTestRule.persistNew(decision1, decision2, decision3);

    // when
    when(permissionsService.getDecisionsWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.all());
    MvcResult mvcResult = postRequest(QUERY_DECISION_GROUPED_URL, new DecisionRequestDto());

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
    searchTestRule.persistNew(decision1, decision2, decision3);

    // when
    when(permissionsService.getDecisionsWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of(decisionId2)));
    MvcResult mvcResult = postRequest(QUERY_DECISION_GROUPED_URL, new DecisionRequestDto());

    // then
    List<DecisionGroupDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(1);
    assertThat(response.stream().map(DecisionGroupDto::getDecisionId).collect(Collectors.toList()))
        .containsExactly(decisionId2);
  }

  @Test
  public void testDecisionsGroupedWithTenantId() throws Exception {
    // given
    String id111 = "111";
    String id121 = "121";
    String id112 = "112";
    String id122 = "122";
    String id2 = "222";
    String id3 = "333";
    String decisionId1 = "decisionId1";
    String decisionId2 = "decisionId2";
    String decisionId3 = "decisionId3";
    String tenantId1 = "tenant1";
    String tenantId2 = "tenant2";

    final DecisionDefinitionEntity decision1_1_1 =
        new DecisionDefinitionEntity()
            .setId(id111)
            .setVersion(1)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId1);
    final DecisionDefinitionEntity decision1_2_1 =
        new DecisionDefinitionEntity()
            .setId(id121)
            .setVersion(2)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId1);
    final DecisionDefinitionEntity decision1_1_2 =
        new DecisionDefinitionEntity()
            .setId(id112)
            .setVersion(1)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId2);
    final DecisionDefinitionEntity decision1_2_2 =
        new DecisionDefinitionEntity()
            .setId(id122)
            .setVersion(2)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId2);
    final DecisionDefinitionEntity decision2 =
        new DecisionDefinitionEntity()
            .setId(id2)
            .setVersion(1)
            .setDecisionId(decisionId2)
            .setTenantId(tenantId1);
    final DecisionDefinitionEntity decision3 =
        new DecisionDefinitionEntity()
            .setId(id3)
            .setVersion(1)
            .setDecisionId(decisionId3)
            .setTenantId(tenantId2);
    searchTestRule.persistNew(
        decision1_1_1, decision1_2_1, decision1_1_2, decision1_2_2, decision2, decision3);

    when(permissionsService.getDecisionsWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.all());

    // when
    MvcResult mvcResult =
        postRequest(QUERY_DECISION_GROUPED_URL, new DecisionRequestDto().setTenantId(tenantId1));

    // then
    List<DecisionGroupDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(2);
    assertThat(response.stream().map(DecisionGroupDto::getTenantId).collect(Collectors.toList()))
        .containsOnly(tenantId1);
    assertThat(response)
        .filteredOn(g -> g.getDecisionId().equals(decisionId1))
        .flatMap(g -> g.getDecisions())
        .extracting("id")
        .containsExactlyInAnyOrder(id111, id121);

    // when
    mvcResult =
        postRequest(QUERY_DECISION_GROUPED_URL, new DecisionRequestDto().setTenantId(tenantId2));

    // then
    response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(2);
    assertThat(response.stream().map(DecisionGroupDto::getTenantId).collect(Collectors.toList()))
        .containsOnly(tenantId2);
    assertThat(response)
        .filteredOn(g -> g.getDecisionId().equals(decisionId1))
        .flatMap(g -> g.getDecisions())
        .extracting("id")
        .containsExactlyInAnyOrder(id112, id122);

    // when
    mvcResult = postRequest(QUERY_DECISION_GROUPED_URL, new DecisionRequestDto().setTenantId(null));

    // then
    response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(4);
    assertThat(response.stream().map(DecisionGroupDto::getTenantId).collect(Collectors.toList()))
        .containsOnly(tenantId1, tenantId2);
    assertThat(response)
        .filteredOn(g -> g.getDecisionId().equals(decisionId1) && g.getTenantId().equals(tenantId1))
        .flatMap(g -> g.getDecisions())
        .extracting("id")
        .containsExactlyInAnyOrder(id111, id121);
    assertThat(response)
        .filteredOn(g -> g.getDecisionId().equals(decisionId1) && g.getTenantId().equals(tenantId2))
        .flatMap(g -> g.getDecisions())
        .extracting("id")
        .containsExactlyInAnyOrder(id112, id122);
  }

  @Test
  public void testDecisionsGroupedFilteredByUserTenants() throws Exception {
    // given
    String id111 = "111";
    String id121 = "121";
    String id112 = "112";
    String id122 = "122";
    String id2 = "222";
    String id3 = "333";
    String decisionId1 = "decisionId1";
    String decisionId2 = "decisionId2";
    String decisionId3 = "decisionId3";
    String tenantId1 = "tenant1";
    String tenantId2 = "tenant2";
    doReturn(TenantService.AuthenticatedTenants.assignedTenants(List.of(tenantId1)))
        .when(tenantService)
        .getAuthenticatedTenants();

    final DecisionDefinitionEntity decision1_1_1 =
        new DecisionDefinitionEntity()
            .setId(id111)
            .setVersion(1)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId1);
    final DecisionDefinitionEntity decision1_2_1 =
        new DecisionDefinitionEntity()
            .setId(id121)
            .setVersion(2)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId1);
    final DecisionDefinitionEntity decision1_1_2 =
        new DecisionDefinitionEntity()
            .setId(id112)
            .setVersion(1)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId2);
    final DecisionDefinitionEntity decision1_2_2 =
        new DecisionDefinitionEntity()
            .setId(id122)
            .setVersion(2)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId2);
    final DecisionDefinitionEntity decision2 =
        new DecisionDefinitionEntity()
            .setId(id2)
            .setVersion(1)
            .setDecisionId(decisionId2)
            .setTenantId(tenantId1);
    final DecisionDefinitionEntity decision3 =
        new DecisionDefinitionEntity()
            .setId(id3)
            .setVersion(1)
            .setDecisionId(decisionId3)
            .setTenantId(tenantId2);
    searchTestRule.persistNew(
        decision1_1_1, decision1_2_1, decision1_1_2, decision1_2_2, decision2, decision3);

    when(permissionsService.getDecisionsWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.all());

    // when
    MvcResult mvcResult = postRequest(QUERY_DECISION_GROUPED_URL, new DecisionRequestDto());

    // then
    List<DecisionGroupDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(response).hasSize(2);
    assertThat(response.stream().map(DecisionGroupDto::getTenantId).collect(Collectors.toList()))
        .containsOnly(tenantId1);
    assertThat(response)
        .filteredOn(g -> g.getDecisionId().equals(decisionId1))
        .flatMap(g -> g.getDecisions())
        .extracting("id")
        .containsExactlyInAnyOrder(id111, id121);

    // when
    mvcResult =
        postRequest(QUERY_DECISION_GROUPED_URL, new DecisionRequestDto().setTenantId(tenantId2));

    // then
    response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(response).hasSize(0);

    // when
    mvcResult = postRequest(QUERY_DECISION_GROUPED_URL, new DecisionRequestDto().setTenantId(null));

    // then
    response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(2);
    assertThat(response.stream().map(DecisionGroupDto::getTenantId).collect(Collectors.toList()))
        .containsOnly(tenantId1);
    assertThat(response)
        .filteredOn(g -> g.getDecisionId().equals(decisionId1))
        .flatMap(g -> g.getDecisions())
        .extracting("id")
        .containsExactlyInAnyOrder(id111, id121);
  }

  @Test
  public void testDecisionXMLWithUserTenantCheck() throws Exception {
    // given
    String id111 = "111";
    String id112 = "112";
    String id2 = "222";
    String id3 = "333";
    String decisionId1 = "decisionId1";
    String decisionId2 = "decisionId2";
    String decisionId3 = "decisionId3";
    long decisionReqId1 = 1;
    long decisionReqId2 = 2;
    String tenantId1 = "tenant1";
    String tenantId2 = "tenant2";
    String tenant1Xml = "<xml>tenant1<xml>";
    String tenant2Xml = "<xml>tenant2<xml>";
    doReturn(TenantService.AuthenticatedTenants.assignedTenants(List.of(tenantId1)))
        .when(tenantService)
        .getAuthenticatedTenants();

    final DecisionDefinitionEntity decision1_1_1 =
        new DecisionDefinitionEntity()
            .setId(id111)
            .setKey(Long.valueOf(id111))
            .setVersion(1)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId1)
            .setDecisionRequirementsKey(decisionReqId1);
    final DecisionDefinitionEntity decision1_1_2 =
        new DecisionDefinitionEntity()
            .setId(id112)
            .setKey(Long.valueOf(id112))
            .setVersion(1)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId2)
            .setDecisionRequirementsKey(decisionReqId2);
    final DecisionDefinitionEntity decision2 =
        new DecisionDefinitionEntity()
            .setId(id2)
            .setKey(Long.valueOf(id2))
            .setVersion(1)
            .setDecisionId(decisionId2)
            .setTenantId(tenantId1)
            .setDecisionRequirementsKey(decisionReqId1);
    final DecisionDefinitionEntity decision3 =
        new DecisionDefinitionEntity()
            .setId(id3)
            .setKey(Long.valueOf(id3))
            .setVersion(1)
            .setDecisionId(decisionId3)
            .setTenantId(tenantId2)
            .setDecisionRequirementsKey(decisionReqId2);
    final DecisionRequirementsEntity decisionReq1 =
        new DecisionRequirementsEntity()
            .setId(String.valueOf(decisionReqId1))
            .setKey(decisionReqId1)
            .setXml(tenant1Xml)
            .setTenantId(tenantId1);
    final DecisionRequirementsEntity decisionReq2 =
        new DecisionRequirementsEntity()
            .setId(String.valueOf(decisionReqId2))
            .setKey(decisionReqId2)
            .setXml(tenant2Xml)
            .setTenantId(tenantId2);
    searchTestRule.persistNew(
        decision1_1_1, decisionReq1, decision1_1_2, decisionReq2, decision2, decision3);

    when(permissionsService.hasPermissionForDecision(decisionId1, IdentityPermission.READ))
        .thenReturn(true);

    // when
    MvcResult mvcResult =
        getRequest(String.format(QUERY_DECISION_XML_URL_PATTERN, id111), MediaType.TEXT_PLAIN);

    // then
    assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo(tenant1Xml);

    // when
    mvcResult =
        getRequestShouldFailWithException(
            String.format(QUERY_DECISION_XML_URL_PATTERN, id112), NotFoundException.class);
  }
}
