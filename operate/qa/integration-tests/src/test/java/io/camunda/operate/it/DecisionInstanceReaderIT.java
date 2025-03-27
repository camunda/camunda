/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_ID_1;
import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_ID_2;
import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_INSTANCE_ID_1_1;
import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_INSTANCE_ID_1_2;
import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_INSTANCE_ID_1_3;
import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_INSTANCE_ID_2_1;
import static io.camunda.operate.data.util.DecisionDataUtil.DECISION_INSTANCE_ID_2_2;
import static io.camunda.operate.webapp.rest.DecisionInstanceRestService.DECISION_INSTANCE_URL;
import static io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto.DECISION_INSTANCE_INPUT_DTO_COMPARATOR;
import static io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto.DECISION_INSTANCE_OUTPUT_DTO_COMPARATOR;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.data.util.DecisionDataUtil;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.webapp.rest.dto.dmn.DRDDataEntryDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceState;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class DecisionInstanceReaderIT extends OperateAbstractIT {

  private static final String QUERY_DECISION_INSTANCES_URL = DECISION_INSTANCE_URL + "/%s";

  @Rule public SearchTestRule searchTestRule = new SearchTestRule();

  @Autowired private DecisionDataUtil testDataUtil;

  @Test
  public void testGetDecisionInstanceByCorrectId() throws Exception {
    final DecisionInstanceEntity entity = createData();

    final MvcResult mvcResult = getRequest(getQuery(DECISION_INSTANCE_ID_1_1));
    final DecisionInstanceDto response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getDecisionDefinitionId()).isEqualTo(entity.getDecisionDefinitionId());
    assertThat(response.getDecisionId()).isEqualTo(entity.getDecisionId());
    assertThat(response.getDecisionName()).isEqualTo(entity.getDecisionName());
    assertThat(response.getDecisionVersion()).isEqualTo(entity.getDecisionVersion());
    assertThat(response.getDecisionType()).isEqualTo(entity.getDecisionType());
    assertThat(response.getErrorMessage()).isEqualTo(entity.getEvaluationFailure());
    assertThat(response.getEvaluationDate())
        .isEqualTo(entity.getEvaluationDate().truncatedTo(ChronoUnit.MILLIS));
    assertThat(response.getId()).isEqualTo(entity.getId());
    assertThat(response.getProcessInstanceId())
        .isEqualTo(String.valueOf(entity.getProcessInstanceKey()));
    assertThat(response.getResult()).isEqualTo(entity.getResult());
    assertThat(response.getState().toString()).isEqualTo(entity.getState().toString());

    assertThat(response.getEvaluatedInputs()).hasSize(2);
    assertThat(response.getEvaluatedInputs())
        .isSortedAccordingTo(DECISION_INSTANCE_INPUT_DTO_COMPARATOR);
    assertThat(response.getEvaluatedOutputs()).hasSize(3);
    assertThat(response.getEvaluatedOutputs())
        .isSortedAccordingTo(DECISION_INSTANCE_OUTPUT_DTO_COMPARATOR);
  }

  @Test
  public void testGetDecisionInstanceDrdData() throws Exception {
    createData();

    // query completed instances
    MvcResult mvcResult = getRequest(getDrdDataQuery(DECISION_INSTANCE_ID_1_1));
    Map<String, List<DRDDataEntryDto>> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(2);
    assertThat(response.get(DECISION_ID_1)).isNotNull();
    assertThat(response.get(DECISION_ID_1)).hasSize(1);
    assertThat(response.get(DECISION_ID_1).get(0).getDecisionInstanceId())
        .isEqualTo(DECISION_INSTANCE_ID_1_1);
    assertThat(response.get(DECISION_ID_1).get(0).getState())
        .isEqualTo(DecisionInstanceState.EVALUATED);

    assertThat(response.get(DECISION_ID_2)).isNotNull();
    assertThat(response.get(DECISION_ID_2)).hasSize(2);
    assertThat(response.get(DECISION_ID_2).get(0).getDecisionInstanceId())
        .isEqualTo(DECISION_INSTANCE_ID_1_2);
    assertThat(response.get(DECISION_ID_2).get(0).getState())
        .isEqualTo(DecisionInstanceState.EVALUATED);
    assertThat(response.get(DECISION_ID_2).get(1).getDecisionInstanceId())
        .isEqualTo(DECISION_INSTANCE_ID_1_3);
    assertThat(response.get(DECISION_ID_2).get(1).getState())
        .isEqualTo(DecisionInstanceState.EVALUATED);

    // query completed and failed
    mvcResult = getRequest(getDrdDataQuery(DECISION_INSTANCE_ID_2_1));
    response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).hasSize(2);
    assertThat(response.get(DECISION_ID_1)).isNotNull();
    assertThat(response.get(DECISION_ID_1)).hasSize(1);
    assertThat(response.get(DECISION_ID_1).get(0).getDecisionInstanceId())
        .isEqualTo(DECISION_INSTANCE_ID_2_1);
    assertThat(response.get(DECISION_ID_1).get(0).getState())
        .isEqualTo(DecisionInstanceState.FAILED);

    assertThat(response.get(DECISION_ID_2)).isNotNull();
    assertThat(response.get(DECISION_ID_2).get(0).getDecisionInstanceId())
        .isEqualTo(DECISION_INSTANCE_ID_2_2);
    assertThat(response.get(DECISION_ID_2).get(0).getState())
        .isEqualTo(DecisionInstanceState.FAILED);
  }

  @Test
  public void testGetDecisionInstanceDrdDataByWrongId() throws Exception {
    createData();
    final MvcResult mvcResult =
        getRequestShouldFailWithException(getDrdDataQuery("55555-1"), NotFoundException.class);
    assertThat(mvcResult.getResolvedException().getMessage())
        .contains("Decision instance nor found: 55555-1");
  }

  @Test
  public void testGetDecisionInstanceByWrongId() throws Exception {
    createData();

    getRequestShouldFailWithException(getQuery("867293586"), NotFoundException.class);
  }

  private DecisionInstanceEntity createData() throws PersistenceException {
    final List<DecisionInstanceEntity> decisionInstances = testDataUtil.createDecisionInstances();
    searchTestRule.persistOperateEntitiesNew(decisionInstances);
    return decisionInstances.get(0);
  }

  private String getQuery(final String decisionInstanceId) {
    return String.format(QUERY_DECISION_INSTANCES_URL, decisionInstanceId);
  }

  private String getDrdDataQuery(final String decisionInstanceId) {
    return String.format(QUERY_DECISION_INSTANCES_URL + "/drd-data", decisionInstanceId);
  }
}
