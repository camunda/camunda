/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_4;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.migration.MigrationTaskContextImpl;
import io.camunda.zeebe.engine.state.migration.to_8_4.legacy.LegacySignalSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.signal.DbSignalSubscriptionState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

public class MultiTenancyMigrationTest {

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class MigrateSignalSubscriptionStateForMultiTenancyTest {

    final MultiTenancySignalSubscriptionStateMigration sut =
        new MultiTenancySignalSubscriptionStateMigration();
    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;

    private LegacySignalSubscriptionState legacyState;
    private DbSignalSubscriptionState signalSubscriptionState;

    @BeforeEach
    void setup() {
      legacyState = new LegacySignalSubscriptionState(zeebeDb, transactionContext);
      signalSubscriptionState = new DbSignalSubscriptionState(zeebeDb, transactionContext);
    }

    @Test
    void shouldMigrateSignalNameAndSubscriptionKeyColumnFamily() {
      // given
      final String signalName = "test";
      final String processId = "testProcess";
      final String catchEventId = "catchEvent";
      final long processDefinitionKey = 1L;
      final long signalSubscriptionKey = 2L;
      final long catchEventInstanceKey = 3L;
      final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;

      final SignalSubscriptionRecord signalSubscription =
          new SignalSubscriptionRecord()
              .setSignalName(BufferUtil.wrapString(signalName))
              .setBpmnProcessId(BufferUtil.wrapString(processId))
              .setCatchEventId(BufferUtil.wrapString(catchEventId))
              .setProcessDefinitionKey(processDefinitionKey)
              .setCatchEventInstanceKey(catchEventInstanceKey);

      legacyState.put(signalSubscriptionKey, signalSubscription);

      // when
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      final AtomicInteger subscriptionCounter = new AtomicInteger(0);
      signalSubscriptionState.visitBySignalName(
          BufferUtil.wrapString(signalName),
          tenantId,
          subscription -> {
            Assertions.assertThat(signalSubscription)
                .hasSignalName(signalName)
                .hasBpmnProcessId(processId)
                .hasCatchEventId(catchEventId)
                .hasProcessDefinitionKey(processDefinitionKey)
                .hasCatchEventInstanceKey(catchEventInstanceKey)
                .hasTenantId(tenantId);
            subscriptionCounter.incrementAndGet();
          });
      assertThat(subscriptionCounter).hasValue(1);
      assertThat(legacyState.getSignalNameAndSubscriptionKeyColumnFamily().isEmpty()).isTrue();
    }

    @Test
    void shouldMigrateSubscriptionKeyAndSignalNameColumnFamilyForProcessDefinitionKey() {
      // given
      final String signalName = "test";
      final String processId = "testProcess";
      final String catchEventId = "catchEvent";
      final long processDefinitionKey = 1L;
      final long signalSubscriptionKey = 2L;
      final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;

      final SignalSubscriptionRecord signalSubscription =
          new SignalSubscriptionRecord()
              .setSignalName(BufferUtil.wrapString(signalName))
              .setBpmnProcessId(BufferUtil.wrapString(processId))
              .setCatchEventId(BufferUtil.wrapString(catchEventId))
              .setProcessDefinitionKey(processDefinitionKey);

      legacyState.put(signalSubscriptionKey, signalSubscription);

      // when
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      final AtomicInteger subscriptionCounter = new AtomicInteger(0);
      signalSubscriptionState.visitStartEventSubscriptionsByProcessDefinitionKey(
          processDefinitionKey,
          subscription -> {
            Assertions.assertThat(signalSubscription)
                .hasSignalName(signalName)
                .hasBpmnProcessId(processId)
                .hasCatchEventId(catchEventId)
                .hasProcessDefinitionKey(processDefinitionKey)
                .hasCatchEventInstanceKey(-1)
                .hasTenantId(tenantId);
            subscriptionCounter.incrementAndGet();
          });
      assertThat(subscriptionCounter).hasValue(1);
      assertThat(legacyState.getSubscriptionKeyAndSignalNameColumnFamily().isEmpty()).isTrue();
    }

    @Test
    void shouldMigrateSubscriptionKeyAndSignalNameColumnFamilyForCatchKey() {
      // given
      final String signalName = "test";
      final String processId = "testProcess";
      final String catchEventId = "catchEvent";
      final long signalSubscriptionKey = 2L;
      final long catchEventInstanceKey = 3L;
      final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;

      final SignalSubscriptionRecord signalSubscription =
          new SignalSubscriptionRecord()
              .setSignalName(BufferUtil.wrapString(signalName))
              .setBpmnProcessId(BufferUtil.wrapString(processId))
              .setCatchEventId(BufferUtil.wrapString(catchEventId))
              .setCatchEventInstanceKey(catchEventInstanceKey);

      legacyState.put(signalSubscriptionKey, signalSubscription);

      // when
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      final AtomicInteger subscriptionCounter = new AtomicInteger(0);
      signalSubscriptionState.visitStartEventSubscriptionsByProcessDefinitionKey(
          catchEventInstanceKey,
          subscription -> {
            Assertions.assertThat(signalSubscription)
                .hasSignalName(signalName)
                .hasBpmnProcessId(processId)
                .hasCatchEventId(catchEventId)
                .hasProcessDefinitionKey(-1)
                .hasCatchEventInstanceKey(catchEventInstanceKey)
                .hasTenantId(tenantId);
            subscriptionCounter.incrementAndGet();
          });
      assertThat(subscriptionCounter).hasValue(1);
      assertThat(legacyState.getSubscriptionKeyAndSignalNameColumnFamily().isEmpty()).isTrue();
    }
  }
}
