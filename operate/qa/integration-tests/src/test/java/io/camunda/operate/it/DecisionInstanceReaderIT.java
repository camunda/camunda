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
import static io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto.DECISION_INSTANCE_INPUT_DTO_COMPARATOR;
import static io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto.DECISION_INSTANCE_OUTPUT_DTO_COMPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

import io.camunda.operate.data.util.DecisionDataUtil;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.webapp.reader.DecisionInstanceReader;
import io.camunda.operate.webapp.rest.dto.dmn.DRDDataEntryDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceState;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DecisionInstanceReaderIT extends OperateAbstractIT {

  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
  private DecisionInstanceEntity entity;

  @Autowired private DecisionDataUtil testDataUtil;
  @Autowired private DecisionInstanceReader decisionInstanceReader;

  @Before
  public void setup() throws PersistenceException {
    final List<DecisionInstanceEntity> decisionInstances = testDataUtil.createDecisionInstances();
    searchTestRule.persistOperateEntitiesNew(decisionInstances);
    entity = decisionInstances.getFirst();
  }

  @Test
  public void testGetDecisionInstanceByCorrectId() {
    final DecisionInstanceDto result =
        decisionInstanceReader.getDecisionInstance(DECISION_INSTANCE_ID_1_1);

    assertThat(result.getDecisionDefinitionId()).isEqualTo(entity.getDecisionDefinitionId());
    assertThat(result.getDecisionId()).isEqualTo(entity.getDecisionId());
    assertThat(result.getDecisionName()).isEqualTo(entity.getDecisionName());
    assertThat(result.getDecisionVersion()).isEqualTo(entity.getDecisionVersion());
    assertThat(result.getDecisionType()).isEqualTo(entity.getDecisionType());
    assertThat(result.getErrorMessage()).isEqualTo(entity.getEvaluationFailure());
    assertThat(result.getEvaluationDate())
        .isEqualTo(entity.getEvaluationDate().truncatedTo(ChronoUnit.MILLIS));
    assertThat(result.getId()).isEqualTo(entity.getId());
    assertThat(result.getProcessInstanceId())
        .isEqualTo(String.valueOf(entity.getProcessInstanceKey()));
    assertThat(result.getResult()).isEqualTo(entity.getResult());
    assertThat(result.getState().toString()).isEqualTo(entity.getState().toString());

    assertThat(result.getEvaluatedInputs()).hasSize(2);
    assertThat(result.getEvaluatedInputs())
        .isSortedAccordingTo(DECISION_INSTANCE_INPUT_DTO_COMPARATOR);
    assertThat(result.getEvaluatedOutputs()).hasSize(3);
    assertThat(result.getEvaluatedOutputs())
        .isSortedAccordingTo(DECISION_INSTANCE_OUTPUT_DTO_COMPARATOR);
  }

  @Test
  public void testGetDecisionInstanceByIdThrowsNotFoundException() {
    assertThatException()
        .isThrownBy(() -> decisionInstanceReader.getDecisionInstance("none existent instance"))
        .isInstanceOf(NotFoundException.class)
        .withMessage("Could not find decision instance with id 'none existent instance'.");
  }

  @Test
  public void testGetDecisionInstanceDrdData() {
    // search completed
    Map<String, List<DRDDataEntryDto>> response =
        decisionInstanceReader.getDecisionInstanceDRDData(DECISION_INSTANCE_ID_1_1);

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

    // search completed and failed
    response = decisionInstanceReader.getDecisionInstanceDRDData(DECISION_INSTANCE_ID_2_1);

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
}
