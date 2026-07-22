/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.search.schema.SchemaManagerContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
class SchemaReadinessCheckTest {

  @Mock private SchemaManagerContainer schemaManagerContainer;

  @InjectMocks private SchemaReadinessCheck check;

  @Test
  void shouldReturnDownWhenSchemaNotInitialized() {
    // given
    when(schemaManagerContainer.isInitialized()).thenReturn(false);

    // when / then
    assertThat(check.health().getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void shouldReturnUpWhenSchemaInitialized() {
    // given
    when(schemaManagerContainer.isInitialized()).thenReturn(true);

    // when / then
    assertThat(check.health().getStatus()).isEqualTo(Status.UP);
  }
}
