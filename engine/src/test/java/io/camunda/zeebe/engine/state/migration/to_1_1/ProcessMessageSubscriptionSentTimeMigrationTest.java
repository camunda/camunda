/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_1_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.migration.ProcessMessageSubscriptionSentTimeMigration;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

public class ProcessMessageSubscriptionSentTimeMigrationTest {

  final ProcessMessageSubscriptionSentTimeMigration sutMigration =
      new ProcessMessageSubscriptionSentTimeMigration();

  @Nested
  public class MockBasedTests {

    @Test
    public void noMigrationNeededWhenColumnIsEmpty() {
      // given
      final var mockProcessingState = mock(ProcessingState.class);
      when(mockProcessingState.isEmpty(ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_SENT_TIME))
          .thenReturn(true);
      // when
      final var actual = sutMigration.needsToRun(mockProcessingState);

      // then
      assertThat(actual).isFalse();
    }

    @Test
    public void migrationNeededWhenColumnIsNotEmpty() {
      // given
      final var mockProcessingState = mock(ProcessingState.class);
      when(mockProcessingState.isEmpty(ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_SENT_TIME))
          .thenReturn(false);

      // when
      final var actual = sutMigration.needsToRun(mockProcessingState);

      // then
      assertThat(actual).isTrue();
    }

    @Test
    public void migrationCallsMethodInMigrationState() {
      // given
      final var mockProcessingState = mock(MutableProcessingState.class, RETURNS_DEEP_STUBS);

      // when
      sutMigration.runMigration(mockProcessingState);

      // then
      verify(mockProcessingState.getMigrationState())
          .migrateProcessMessageSubscriptionSentTime(
              mockProcessingState.getProcessMessageSubscriptionState(),
              mockProcessingState.getPendingProcessMessageSubscriptionState());

      verifyNoMoreInteractions(mockProcessingState.getMigrationState());
    }
  }

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  public class BlackboxTest {

    private static final long TEST_SENT_TIME = 1000L;

    private ZeebeDb<ZbColumnFamilies> zeebeDb;

    private MutableProcessingState processingState;

    private TransactionContext transactionContext;

    @BeforeEach
    public void setUp() {
      // given database with legacy records
      final var legacySubscriptionState =
          new LegacyDbProcessMessageSubscriptionState(zeebeDb, transactionContext);

      final var openingProcessMessageSubscription =
          TestUtilities.createLegacyProcessMessageSubscription(100, 1);
      legacySubscriptionState.put(
          openingProcessMessageSubscription.getKey(),
          openingProcessMessageSubscription.getRecord(),
          TEST_SENT_TIME);
    }

    @Test
    public void migrationNeedsToRun() {
      // given database with legacy records

      // when
      final var actual = sutMigration.needsToRun(processingState);

      // then
      assertThat(actual).describedAs("Migration should run").isTrue();
    }

    @Test
    public void afterMigrationRunNoFurtherMigrationIsNeeded() {
      // given database with legacy records

      // when
      sutMigration.runMigration(processingState);
      final var actual = sutMigration.needsToRun(processingState);

      // then
      assertThat(actual).describedAs("Migration should run").isFalse();
    }
  }
}
