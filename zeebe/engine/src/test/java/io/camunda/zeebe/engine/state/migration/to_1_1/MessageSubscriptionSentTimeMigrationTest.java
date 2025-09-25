/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import io.camunda.zeebe.engine.common.state.migration.MessageSubscriptionSentTimeMigration;
import io.camunda.zeebe.engine.common.state.migration.MigrationTaskContextImpl;
import io.camunda.zeebe.engine.common.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

public class MessageSubscriptionSentTimeMigrationTest {

  final MessageSubscriptionSentTimeMigration sutMigration =
      new MessageSubscriptionSentTimeMigration();

  @Nested
  public class MockBasedTests {

    @Test
    public void noMigrationNeededWhenColumnIsEmpty() {
      // given
      final var mockProcessingState = mock(MutableProcessingState.class);
      when(mockProcessingState.isEmpty(ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_SENT_TIME))
          .thenReturn(true);
      // when
      final var actual =
          sutMigration.needsToRun(
              new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState));

      // then
      assertThat(actual).isFalse();
    }

    @Test
    public void migrationNeededWhenColumnIsNotEmpty() {
      // given
      final var mockProcessingState = mock(MutableProcessingState.class);
      when(mockProcessingState.isEmpty(ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_SENT_TIME))
          .thenReturn(false);

      // when
      final var actual =
          sutMigration.needsToRun(
              new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState));

      // then
      assertThat(actual).isTrue();
    }

    @Test
    public void migrationCallsMethodInMigrationState() {
      // given
      final var mockProcessingState = mock(MutableProcessingState.class, RETURNS_DEEP_STUBS);

      // when
      sutMigration.runMigration(
          new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState));

      // then
      verify(mockProcessingState.getMigrationState())
          .migrateMessageSubscriptionSentTime(
              mockProcessingState.getMessageSubscriptionState(),
              mockProcessingState.getPendingMessageSubscriptionState());

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
      final var legacySubscriptionState =
          new LegacyDbMessageSubscriptionState(zeebeDb, transactionContext);

      final LegacyMessageSubscription subscriptionInCorrelation =
          TestUtilities.createLegacyMessageSubscription(100, 1);
      legacySubscriptionState.put(
          subscriptionInCorrelation.getKey(), subscriptionInCorrelation.getRecord());
      legacySubscriptionState.updateSentTime(subscriptionInCorrelation, TEST_SENT_TIME);
    }

    @Test
    public void migrationNeedsToRun() {
      // given database with legacy records

      // when
      final var actual =
          sutMigration.needsToRun(
              new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      assertThat(actual).describedAs("Migration should run").isTrue();
    }

    @Test
    public void afterMigrationRunNoFurtherMigrationIsNeeded() {
      // given database with legacy records

      // when
      final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
      sutMigration.runMigration(context);
      final var actual = sutMigration.needsToRun(context);

      // then
      assertThat(actual).describedAs("Migration should run").isFalse();
    }
  }
}
