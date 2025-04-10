/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.data.util.DecisionDataUtil;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.webapp.reader.DecisionReader;
import io.camunda.operate.webapp.rest.dto.DecisionRequestDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionGroupDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class DecisionZeebeImportIT extends OperateZeebeAbstractIT {

  private static final String QUERY_DECISIONS_GROUPED_URL = "/api/decisions/grouped";
  private static final String QUERY_DECISION_XML_URL = "/api/decisions/%s/xml";

  @Rule public SearchTestRule searchTestRule = new SearchTestRule();

  @Autowired private DecisionDataUtil testDataUtil;

  @Autowired private DecisionReader decisionReader;

  @MockBean private PermissionsService permissionsService;

  @Override
  @Before
  public void before() {
    super.before();
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.hasPermissionForDecision(any(), any())).thenReturn(true);
  }

  @Test
  public void testDecisionsGrouped() throws Exception {
    // given
    final String demoDecisionId1 = "invoiceClassification";
    final String decision1Name = "Invoice Classification";
    final String demoDecisionId2 = "invoiceAssignApprover";
    final String decision2Name = "Assign Approver Group";

    tester
        .deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .deployDecision("invoiceBusinessDecisions_v_2.dmn")
        .waitUntil()
        // each DRD has two decisions
        .decisionsAreDeployed(4);

    // when
    final MockHttpServletRequestBuilder request = get(QUERY_DECISIONS_GROUPED_URL);
    final MvcResult mvcResult =
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andExpect(content().contentType(mockMvcTestRule.getContentType()))
            .andReturn();

    // then
    final List<DecisionGroupDto> decisionGroupDtos =
        mockMvcTestRule.listFromResponse(mvcResult, DecisionGroupDto.class);
    assertThat(decisionGroupDtos).hasSize(2);
    assertThat(decisionGroupDtos)
        .isSortedAccordingTo(new DecisionGroupDto.DecisionGroupComparator());

    assertThat(decisionGroupDtos)
        .filteredOn(wg -> wg.getDecisionId().equals(demoDecisionId1))
        .hasSize(1);
    final DecisionGroupDto demoDecisionGroup1 =
        decisionGroupDtos.stream()
            .filter(wg -> wg.getDecisionId().equals(demoDecisionId1))
            .findFirst()
            .get();
    assertThat(demoDecisionGroup1.getDecisions()).hasSize(2);
    assertThat(demoDecisionGroup1.getName()).isEqualTo(decision1Name);
    assertThat(demoDecisionGroup1.getDecisions())
        .isSortedAccordingTo(
            (w1, w2) -> Integer.valueOf(w2.getVersion()).compareTo(w1.getVersion()));
    assertThat(demoDecisionGroup1.getDecisions().get(0).getId())
        .isNotEqualTo(demoDecisionGroup1.getDecisions().get(1).getId());

    assertThat(decisionGroupDtos)
        .filteredOn(wg -> wg.getDecisionId().equals(demoDecisionId2))
        .hasSize(1);
    final DecisionGroupDto demoDecisionGroup2 =
        decisionGroupDtos.stream()
            .filter(wg -> wg.getDecisionId().equals(demoDecisionId2))
            .findFirst()
            .get();
    assertThat(demoDecisionGroup2.getDecisions()).hasSize(2);
    assertThat(demoDecisionGroup2.getName()).isEqualTo(decision2Name);
    assertThat(demoDecisionGroup2.getDecisions())
        .isSortedAccordingTo(
            (w1, w2) -> Integer.valueOf(w2.getVersion()).compareTo(w1.getVersion()));
    assertThat(demoDecisionGroup2.getDecisions().get(0).getId())
        .isNotEqualTo(demoDecisionGroup1.getDecisions().get(1).getId());

    verify(permissionsService, times(2)).getDecisionDefinitionPermissions(anyString());
  }

  @Test
  public void testDecisionGetDiagramV1() throws Exception {
    // given
    final Long decision1V1Id = 1222L;
    final Long decision2V1Id = 1333L;

    createData();

    // when invoiceClassification version 1
    MockHttpServletRequestBuilder request =
        get(String.format(QUERY_DECISION_XML_URL, decision1V1Id));
    MvcResult mvcResult = mockMvc.perform(request).andExpect(status().isOk()).andReturn();

    final String invoiceClassificationVersion1 = mvcResult.getResponse().getContentAsString();

    // and invoiceClassification version 1
    request = get(String.format(QUERY_DECISION_XML_URL, decision2V1Id));
    mvcResult = mockMvc.perform(request).andExpect(status().isOk()).andReturn();

    final String invoiceAssignApproverVersion1 = mvcResult.getResponse().getContentAsString();

    // then one and the same DRD is returned
    assertThat(invoiceAssignApproverVersion1).isEqualTo(invoiceClassificationVersion1);

    // it is of version 1
    assertThat(invoiceAssignApproverVersion1).isNotEmpty();
    assertThat(invoiceAssignApproverVersion1)
        .contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    assertThat(invoiceAssignApproverVersion1).doesNotContain("exceptional");
  }

  @Test
  public void testDecisionGetDiagramV2() throws Exception {
    // given
    final Long decision1V2Id = 2222L;
    final Long decision2V2Id = 2333L;

    createData();

    // when invoiceClassification version 2
    MockHttpServletRequestBuilder request =
        get(String.format(QUERY_DECISION_XML_URL, decision1V2Id));
    MvcResult mvcResult = mockMvc.perform(request).andExpect(status().isOk()).andReturn();

    final String invoiceClassificationVersion2 = mvcResult.getResponse().getContentAsString();

    // and invoiceClassification version 2
    request = get(String.format(QUERY_DECISION_XML_URL, decision2V2Id));
    mvcResult = mockMvc.perform(request).andExpect(status().isOk()).andReturn();

    final String invoiceAssignApproverVersion2 = mvcResult.getResponse().getContentAsString();

    // then
    // one and the same DRD is returned
    assertThat(invoiceAssignApproverVersion2).isEqualTo(invoiceClassificationVersion2);
    // it is of version 2
    assertThat(invoiceAssignApproverVersion2).isNotEmpty();
    assertThat(invoiceAssignApproverVersion2)
        .contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    assertThat(invoiceAssignApproverVersion2).contains("exceptional");
  }

  @Test
  public void testNonExistingDecisionGetDiagram() throws Exception {
    // given
    final String decisionDefinitionId = "111";
    // no decisions deployed

    // when
    final MockHttpServletRequestBuilder request =
        get(String.format(QUERY_DECISION_XML_URL, decisionDefinitionId));
    mockMvc.perform(request).andExpect(status().isNotFound());
  }

  @Test
  public void testDecisionReaderGetByDecisionDefinitionKey() {
    // given
    final String demoDecisionId1 = "invoiceClassification";
    final String decision1Name = "Invoice Classification";

    tester
        .deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .waitUntil()
        // each DRD has two decisions
        .decisionsAreDeployed(2);

    // when
    final Map<String, List<DecisionDefinitionEntity>> decisionsGrouped =
        decisionReader.getDecisionsGrouped(new DecisionRequestDto());
    final DecisionDefinitionEntity entity1 = decisionsGrouped.values().iterator().next().get(0);
    final Long decisionDefinitionKey = Long.valueOf(entity1.getId());
    final DecisionDefinitionEntity entity2 = decisionReader.getDecision(decisionDefinitionKey);

    // then
    assertThat(entity2.getId()).isEqualTo(entity1.getId());
    assertThat(entity2.getName()).isEqualTo(entity1.getName());
    assertThat(entity2.getDecisionId()).isEqualTo(entity1.getDecisionId());
  }

  private void createData() throws PersistenceException {
    searchTestRule.persistOperateEntitiesNew(testDataUtil.createDecisionDefinitions());
  }
}
