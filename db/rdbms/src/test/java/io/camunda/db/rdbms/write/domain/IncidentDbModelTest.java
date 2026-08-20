/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.IncidentEntity.IncidentState;
import org.junit.jupiter.api.Test;

class IncidentDbModelTest {

  @Test
  void shouldTruncateErrorMessage() {
    final IncidentDbModel truncatedMessage =
        new IncidentDbModel.Builder()
            .incidentKey(123L)
            .processInstanceKey(456L)
            .rootProcessInstanceKey(654L)
            .processDefinitionKey(789L)
            .errorMessage("errorMessage")
            .state(IncidentState.ACTIVE)
            .tenantId("tenantId")
            .build()
            .truncateErrorMessage(10, null);

    assertThat(truncatedMessage.errorMessage().length()).isEqualTo(10);

    assertThat(truncatedMessage)
        .isEqualTo(
            new IncidentDbModel.Builder()
                .incidentKey(123L)
                .processInstanceKey(456L)
                .rootProcessInstanceKey(654L)
                .processDefinitionKey(789L)
                .errorMessage("errorMessa")
                .state(IncidentState.ACTIVE)
                .tenantId("tenantId")
                .build());
  }

  @Test
  void shouldTruncateErrorMessageBytes() {
    final IncidentDbModel truncatedMessage =
        new IncidentDbModel.Builder()
            .incidentKey(123L)
            .processInstanceKey(456L)
            .rootProcessInstanceKey(654L)
            .processDefinitionKey(789L)
            .errorMessage("ääääääääää")
            .state(IncidentState.ACTIVE)
            .tenantId("tenantId")
            .build()
            .truncateErrorMessage(50, 5);

    assertThat(truncatedMessage.errorMessage().length()).isEqualTo(2);
    assertThat(truncatedMessage.errorMessage()).isEqualTo("ää");
  }
}
