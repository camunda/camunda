/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class DecisionInstanceDbModelTest {

  @Test
  void shouldTruncateErrorMessage() {
    final DecisionInstanceDbModel truncatedMessage =
        new DecisionInstanceDbModel.Builder()
            .decisionInstanceId("decisionInstanceId")
            .decisionInstanceKey(1L)
            .state(DecisionInstanceState.EVALUATED)
            .evaluationDate(OffsetDateTime.now())
            .evaluationFailure("evaluationFailure")
            .evaluationFailureMessage("errorMessage")
            .result("result")
            .flowNodeInstanceKey(2L)
            .flowNodeId("flowNodeId")
            .processInstanceKey(3L)
            .processDefinitionKey(4L)
            .processDefinitionId("processDefinitionId")
            .decisionDefinitionKey(5L)
            .decisionDefinitionId("decisionDefinitionId")
            .decisionRequirementsKey(6L)
            .decisionRequirementsId("decisionRequirementsId")
            .build()
            .truncateErrorMessage(10);

    assertThat(truncatedMessage.evaluationFailureMessage().length()).isEqualTo(10);
    assertThat(truncatedMessage.evaluationFailureMessage()).isEqualTo("errorMessa");
  }

  @Test
  void shouldNotFailOnTruncateErrorMessageIfNoMessageIsSet() {
    final DecisionInstanceDbModel decisionInstanceDbModel =
        new DecisionInstanceDbModel.Builder()
            .decisionInstanceId("decisionInstanceId")
            .decisionInstanceKey(1L)
            .state(DecisionInstanceState.EVALUATED)
            .evaluationDate(OffsetDateTime.now())
            .evaluationFailure(null)
            .evaluationFailureMessage(null)
            .result("result")
            .flowNodeInstanceKey(2L)
            .flowNodeId("flowNodeId")
            .processInstanceKey(3L)
            .processDefinitionKey(4L)
            .processDefinitionId("processDefinitionId")
            .decisionDefinitionKey(5L)
            .decisionDefinitionId("decisionDefinitionId")
            .decisionRequirementsKey(6L)
            .decisionRequirementsId("decisionRequirementsId")
            .build();

    assertThatCode(() -> decisionInstanceDbModel.truncateErrorMessage(10))
        .doesNotThrowAnyException();
  }
}
