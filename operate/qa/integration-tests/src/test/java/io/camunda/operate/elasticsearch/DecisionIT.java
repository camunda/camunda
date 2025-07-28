/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.webapp.rest.DecisionRestService;
import io.camunda.operate.webapp.rest.dto.DecisionRequestDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionGroupDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.security.reader.TenantAccess;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;
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
    classes = {
      TestApplication.class,
      UnifiedConfigurationHelper.class,
      UnifiedConfiguration.class,
    },
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      OperateProperties.PREFIX + ".zeebe.compatibility.enabled = true",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      "camunda.security.multiTenancy.checksEnabled = true",
      "camunda.security.authentication.unprotected-api=false"
    })
public class DecisionIT extends OperateAbstractIT {

  private static final String QUERY_DECISION_GROUPED_URL =
      DecisionRestService.DECISION_URL + "/grouped";
  private static final String QUERY_DECISION_XML_URL_PATTERN =
      DecisionRestService.DECISION_URL + "/%s/xml";
  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
  @MockBean private PermissionsService permissionsService;

  @Test
  public void testDecisionsGroupedWithPermissionWhenNotAllowed() throws Exception {
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
    searchTestRule.persistNew(decision1, decision2, decision3);

    // when
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.getDecisionsWithPermission(PermissionType.READ_DECISION_DEFINITION))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));
    final MvcResult mvcResult = postRequest(QUERY_DECISION_GROUPED_URL, new DecisionRequestDto());

    // then
    final List<DecisionGroupDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).isEmpty();
  }

  @Test
  public void testDecisionsGroupedWithPermissionWhenAllowed() throws Exception {
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
    searchTestRule.persistNew(decision1, decision2, decision3);

    // when
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.getDecisionsWithPermission(PermissionType.READ_DECISION_DEFINITION))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());
    final MvcResult mvcResult = postRequest(QUERY_DECISION_GROUPED_URL, new DecisionRequestDto());

    // then
    final List<DecisionGroupDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(3);
    assertThat(response.stream().map(DecisionGroupDto::getDecisionId).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(decisionId1, decisionId2, decisionId3);
  }

  @Test
  public void testDecisionsGroupedWithPermissionWhenSomeAllowed() throws Exception {
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
    searchTestRule.persistNew(decision1, decision2, decision3);

    // when
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.getDecisionsWithPermission(PermissionType.READ_DECISION_DEFINITION))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of(decisionId2)));
    final MvcResult mvcResult = postRequest(QUERY_DECISION_GROUPED_URL, new DecisionRequestDto());

    // then
    final List<DecisionGroupDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(1);
    assertThat(response.stream().map(DecisionGroupDto::getDecisionId).collect(Collectors.toList()))
        .containsExactly(decisionId2);
  }

  @Test
  public void testDecisionsGroupedWithTenantId() throws Exception {
    // given
    final String id111 = "111";
    final String id121 = "121";
    final String id112 = "112";
    final String id122 = "122";
    final String id2 = "222";
    final String id3 = "333";
    final String decisionId1 = "decisionId1";
    final String decisionId2 = "decisionId2";
    final String decisionId3 = "decisionId3";
    final String tenantId1 = "tenant1";
    final String tenantId2 = "tenant2";

    final DecisionDefinitionEntity decision111 =
        new DecisionDefinitionEntity()
            .setId(id111)
            .setVersion(1)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId1);
    final DecisionDefinitionEntity decision121 =
        new DecisionDefinitionEntity()
            .setId(id121)
            .setVersion(2)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId1);
    final DecisionDefinitionEntity decision112 =
        new DecisionDefinitionEntity()
            .setId(id112)
            .setVersion(1)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId2);
    final DecisionDefinitionEntity decision122 =
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
        decision111, decision121, decision112, decision122, decision2, decision3);

    when(permissionsService.getDecisionsWithPermission(PermissionType.READ_DECISION_DEFINITION))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());

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
    final String id111 = "111";
    final String id121 = "121";
    final String id112 = "112";
    final String id122 = "122";
    final String id2 = "222";
    final String id3 = "333";
    final String decisionId1 = "decisionId1";
    final String decisionId2 = "decisionId2";
    final String decisionId3 = "decisionId3";
    final String tenantId1 = "tenant1";
    final String tenantId2 = "tenant2";
    doReturn(TenantAccess.allowed(List.of(tenantId1)))
        .when(tenantService)
        .getAuthenticatedTenants();

    final DecisionDefinitionEntity decision111 =
        new DecisionDefinitionEntity()
            .setId(id111)
            .setVersion(1)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId1);
    final DecisionDefinitionEntity decision121 =
        new DecisionDefinitionEntity()
            .setId(id121)
            .setVersion(2)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId1);
    final DecisionDefinitionEntity decision112 =
        new DecisionDefinitionEntity()
            .setId(id112)
            .setVersion(1)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId2);
    final DecisionDefinitionEntity decision122 =
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
        decision111, decision121, decision112, decision122, decision2, decision3);

    when(permissionsService.getDecisionsWithPermission(PermissionType.READ_DECISION_DEFINITION))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());

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
    final String id111 = "111";
    final String id112 = "112";
    final String id2 = "222";
    final String id3 = "333";
    final String decisionId1 = "decisionId1";
    final String decisionId2 = "decisionId2";
    final String decisionId3 = "decisionId3";
    final long decisionReqId1 = 1;
    final long decisionReqId2 = 2;
    final String tenantId1 = "tenant1";
    final String tenantId2 = "tenant2";
    final String tenant1Xml = "<xml>tenant1<xml>";
    final String tenant2Xml = "<xml>tenant2<xml>";
    doReturn(TenantAccess.allowed(List.of(tenantId1)))
        .when(tenantService)
        .getAuthenticatedTenants();

    final DecisionDefinitionEntity decision111 =
        new DecisionDefinitionEntity()
            .setId(id111)
            .setKey(Long.valueOf(id111))
            .setVersion(1)
            .setDecisionId(decisionId1)
            .setTenantId(tenantId1)
            .setDecisionRequirementsKey(decisionReqId1);
    final DecisionDefinitionEntity decision112 =
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
        decision111, decisionReq1, decision112, decisionReq2, decision2, decision3);

    when(permissionsService.hasPermissionForDecision(
            decisionId1, PermissionType.READ_DECISION_DEFINITION))
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
