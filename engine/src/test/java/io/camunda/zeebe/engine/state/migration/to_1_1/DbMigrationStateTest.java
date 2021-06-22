/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_1_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.message.ProcessMessageSubscription;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.engine.util.ZeebeStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import java.util.ArrayList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ZeebeStateExtension.class)
public class DbMigrationStateTest {

  private static final long TEST_SENT_TIME = 1000L;

  private ZeebeDb<ZbColumnFamilies> zeebeDb;

  private MutableZeebeState zeebeState;

  private TransactionContext transactionContext;

  @Test
  public void testMigrateProcessMessageSubscriptionSentTime() {
    // given database with legacy records
    final var legacySubscriptionState =
        new LegacyDbProcessMessageSubscriptionState(zeebeDb, transactionContext);
    final var subscriptionState = zeebeState.getProcessMessageSubscriptionState();

    final var openingProcessMessageSubscription =
        TestUtilities.createLegacyProcessMessageSubscription(100, 1);
    legacySubscriptionState.put(
        openingProcessMessageSubscription.getKey(),
        openingProcessMessageSubscription.getRecord(),
        TEST_SENT_TIME);

    final var openedProcessMessageSubscription =
        TestUtilities.createLegacyProcessMessageSubscription(101, 2);
    legacySubscriptionState.put(
        openedProcessMessageSubscription.getKey(),
        openedProcessMessageSubscription.getRecord(),
        TEST_SENT_TIME);
    legacySubscriptionState.updateToOpenedState(openedProcessMessageSubscription.getRecord());

    final var closingProcessMessageSubscription =
        TestUtilities.createLegacyProcessMessageSubscription(102, 3);
    legacySubscriptionState.put(
        closingProcessMessageSubscription.getKey(),
        closingProcessMessageSubscription.getRecord(),
        TEST_SENT_TIME);
    legacySubscriptionState.updateToClosingState(
        closingProcessMessageSubscription.getRecord(), TEST_SENT_TIME);

    // when
    final var migrationState = zeebeState.getMigrationState();
    migrationState.migrateProcessMessageSubscriptionSentTime(
        zeebeState.getProcessMessageSubscriptionState(),
        zeebeState.getPendingProcessMessageSubscriptionState());

    // then
    // the sent time column family is empty
    assertThat(
            zeebeDb.isEmpty(ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_SENT_TIME, transactionContext))
        .describedAs("Column family PROCESS_SUBSCRIPTION_BY_SENT_TIME is empty")
        .isTrue();

    final var migratedOpeningSubscription =
        lookupMigratedProcessMessageSubscription(
            openingProcessMessageSubscription, subscriptionState);
    assertThat(migratedOpeningSubscription.isOpening())
        .describedAs("Opening subscription - opening flag")
        .isTrue();
    assertThat(migratedOpeningSubscription.isClosing())
        .describedAs("Opening subscription - closing flag")
        .isFalse();

    final var migratedOpenedSubscription =
        lookupMigratedProcessMessageSubscription(
            openedProcessMessageSubscription, subscriptionState);
    assertThat(migratedOpenedSubscription.isOpening())
        .describedAs("Opened subscription - opening flag")
        .isFalse();
    assertThat(migratedOpenedSubscription.isClosing())
        .describedAs("Opened subscription - closing flag")
        .isFalse();

    final var migratedClosingSubscription =
        lookupMigratedProcessMessageSubscription(
            closingProcessMessageSubscription, subscriptionState);
    assertThat(migratedClosingSubscription.isOpening())
        .describedAs("Closing subscription - opening flag")
        .isFalse();
    assertThat(migratedClosingSubscription.isClosing())
        .describedAs("Closing subscription - closing flag")
        .isTrue();

    assertThatRecordsArePresentInTransientState(
        openingProcessMessageSubscription.getRecord(),
        closingProcessMessageSubscription.getRecord());
  }

  private void assertThatRecordsArePresentInTransientState(
      final ProcessMessageSubscriptionRecord... subscriptionRecords) {
    final var transientSubscriptionState = zeebeState.getPendingProcessMessageSubscriptionState();

    final var correlatingSubscriptions = new ArrayList<ProcessMessageSubscriptionRecord>();

    transientSubscriptionState.visitSubscriptionBefore(
        TEST_SENT_TIME + 1,
        subscription -> {
          final var copyOfRecord = new ProcessMessageSubscriptionRecord();
          copyOfRecord.wrap(subscription.getRecord());
          correlatingSubscriptions.add(copyOfRecord);
          return true;
        });

    assertThat(correlatingSubscriptions)
        .hasSize(subscriptionRecords.length)
        .containsExactlyInAnyOrder(subscriptionRecords);

    transientSubscriptionState.visitSubscriptionBefore(
        TEST_SENT_TIME,
        subscription -> Assertions.fail("Found unexpected subscription " + subscription));
  }

  private ProcessMessageSubscription lookupMigratedProcessMessageSubscription(
      final LegacyProcessMessageSubscription subscription,
      final MutableProcessMessageSubscriptionState subscriptionState) {
    return subscriptionState.getSubscription(
        subscription.getRecord().getElementInstanceKey(),
        subscription.getRecord().getMessageNameBuffer());
  }
}
