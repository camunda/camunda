/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.enties;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.entities.operation.OperationEntity;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class OperationEntityTest {

  @Test
  void shouldHaveCompletedDateField() {
    final var operation = new OperationEntity();
    // Default value is null
    assertThat(operation.getCompletedDate()).isNull();
    assertThat(operation.setCompletedDate(OffsetDateTime.now()).getCompletedDate())
        .isInstanceOf(OffsetDateTime.class);
  }
}
