/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.state.immutable.MigrationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class MultiTenancyMigrationTest {

  final MultiTenancyMigration sut = new MultiTenancyMigration();

  @Nested
  class MockBasedTests {

    @Test
    void migrationNeededWhenMigrationNotFinished() {
      // given
      final var mockProcessingState = mock(ProcessingState.class);
      final var migrationState = mock(MigrationState.class);
      when(mockProcessingState.getMigrationState()).thenReturn(migrationState);
      when(migrationState.isMigrationFinished(anyString())).thenReturn(false);

      // when
      final var actual = sut.needsToRun(mockProcessingState);

      // then
      assertThat(actual).isTrue();
    }
  }
}
