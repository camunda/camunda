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

import io.camunda.db.rdbms.write.domain.JobDbModel.Builder;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class JobDbModelTest {

  @Test
  void shouldTruncateErrorMessage() {
    final JobDbModel truncatedMessage =
        new JobDbModel.Builder()
            .errorMessage("errorMessage")
            .jobKey(1L)
            .processInstanceKey(2L)
            .processDefinitionKey(3L)
            .elementInstanceKey(4L)
            .elementId("elementId")
            .type("type")
            .retries(1)
            .worker("worker")
            .deadline(OffsetDateTime.now())
            .tenantId("tenantId")
            .build()
            .truncateErrorMessage(10, null);

    assertThat(truncatedMessage.errorMessage().length()).isEqualTo(10);
    assertThat(truncatedMessage.errorMessage()).isEqualTo("errorMessa");
  }

  @Test
  void shouldTruncateErrorMessageBytes() {
    final JobDbModel truncatedMessage =
        new Builder()
            .errorMessage("ääääääääää")
            .jobKey(1L)
            .processInstanceKey(2L)
            .processDefinitionKey(3L)
            .elementInstanceKey(4L)
            .elementId("elementId")
            .type("type")
            .retries(1)
            .worker("worker")
            .deadline(OffsetDateTime.now())
            .tenantId("tenantId")
            .build()
            .truncateErrorMessage(99, 5);

    assertThat(truncatedMessage.errorMessage().length()).isEqualTo(2);
    assertThat(truncatedMessage.errorMessage()).isEqualTo("ää");
  }

  @Test
  void shouldNotFailOnTruncateErrorMessageIfNoMessageIsSet() {
    final var jobDbModel =
        new Builder()
            .jobKey(1L)
            .processInstanceKey(2L)
            .processDefinitionKey(3L)
            .elementInstanceKey(4L)
            .elementId("elementId")
            .type("type")
            .retries(1)
            .worker("worker")
            .deadline(OffsetDateTime.now())
            .tenantId("tenantId")
            .build();

    assertThatCode(() -> jobDbModel.truncateErrorMessage(10, 5)).doesNotThrowAnyException();
  }
}
