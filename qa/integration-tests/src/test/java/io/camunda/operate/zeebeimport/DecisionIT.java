/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.operate.data.util.DecisionDataUtil;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionGroupDto;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class DecisionIT extends OperateIntegrationTest {

  private static final String QUERY_DECISIONS_GROUPED_URL = "/api/decisions/grouped";

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  private DecisionDataUtil testDataUtil;

  @Test
  public void testProcessesGrouped() throws Exception {
    //given
    final String demoDecisionId1 = "invoiceClassification";
    final String decision1Name = "Invoice Classification";
    final Long decision1V1Id = 1222L;
    final Long decision1V2Id = 2222L;
    final String demoDecisionId2 = "invoice-assign-approver";
    final String decision2Name = "Assign Approver Group";
    final Long decision2V1Id = 1333L;
    final Long decision2V2Id = 2333L;

    createData();

    //when
    MockHttpServletRequestBuilder request = get(QUERY_DECISIONS_GROUPED_URL);
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();

    //then
    List<DecisionGroupDto> decisionGroupDtos = mockMvcTestRule.listFromResponse(mvcResult, DecisionGroupDto.class);
    assertThat(decisionGroupDtos).hasSize(2);
    assertThat(decisionGroupDtos).isSortedAccordingTo(new DecisionGroupDto.DecisionGroupComparator());

    assertThat(decisionGroupDtos).filteredOn(wg -> wg.getDecisionId().equals(demoDecisionId1)).hasSize(1);
    final DecisionGroupDto demoDecisionGroup1 =
        decisionGroupDtos.stream().filter(wg -> wg.getDecisionId().equals(demoDecisionId1)).findFirst().get();
    assertThat(demoDecisionGroup1.getDecisions()).hasSize(2);
    assertThat(demoDecisionGroup1.getName()).isEqualTo(decision1Name);
    assertThat(demoDecisionGroup1.getDecisions()).isSortedAccordingTo((w1, w2) -> Integer.valueOf(w2.getVersion()).compareTo(w1.getVersion()));
    assertThat(demoDecisionGroup1.getDecisions()).extracting(DecisionIndex.ID).containsExactlyInAnyOrder(decision1V1Id.toString(), decision1V2Id.toString());

    assertThat(decisionGroupDtos).filteredOn(wg -> wg.getDecisionId().equals(demoDecisionId2)).hasSize(1);
    final DecisionGroupDto demoDecisionGroup2 =
        decisionGroupDtos.stream().filter(wg -> wg.getDecisionId().equals(demoDecisionId2)).findFirst().get();
    assertThat(demoDecisionGroup2.getDecisions()).hasSize(2);
    assertThat(demoDecisionGroup2.getName()).isEqualTo(decision2Name);
    assertThat(demoDecisionGroup2.getDecisions()).isSortedAccordingTo((w1, w2) -> Integer.valueOf(w2.getVersion()).compareTo(w1.getVersion()));
    assertThat(demoDecisionGroup2.getDecisions()).extracting(DecisionIndex.ID).containsExactlyInAnyOrder(decision2V1Id.toString(), decision2V2Id.toString());

  }

  private void createData() throws PersistenceException {
    elasticsearchTestRule.persistOperateEntitiesNew(testDataUtil.createDecisionDefinitions());
  }

}
