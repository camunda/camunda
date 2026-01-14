/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.conditional;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableConditionalSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalSubscriptionRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class ConditionalSubscriptionStateTest {

  private static final String DEFAULT_TENANT = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  private static final String TENANT_1 = "tenant-1";
  private static final String TENANT_2 = "tenant-2";

  public MutableProcessingState processingState;

  private MutableConditionalSubscriptionState state;

  @BeforeEach
  public void setUp() {
    state = processingState.getConditionalSubscriptionState();
  }

  private ConditionalSubscriptionRecord createStartEventSubscription(
      final long processDefinitionKey, final String tenantId) {
    return createSubscription(-1, processDefinitionKey, tenantId);
  }

  private ConditionalSubscriptionRecord createSubscription(
      final long scopeKey, final long processDefinitionKey, final String tenantId) {
    final ConditionalSubscriptionRecord record = new ConditionalSubscriptionRecord();
    record
        .setScopeKey(scopeKey)
        .setProcessDefinitionKey(processDefinitionKey)
        .setBpmnProcessId(BufferUtil.wrapString("test-process"))
        .setCatchEventId(BufferUtil.wrapString("start-" + processDefinitionKey))
        .setCondition(BufferUtil.wrapString("=x > 1"))
        .setVariableNames(List.of("x"))
        .setTenantId(tenantId);
    return record;
  }

  @Nested
  class PutTest {

    @Test
    void shouldPutOnlyForGivenKeyAndTenantId() {
      // given
      final ConditionalSubscriptionRecord subscription =
          createStartEventSubscription(100L, TENANT_1);
      final var key = 1L;

      // when
      state.put(key, subscription);

      // then
      assertThat(state.exists(TENANT_1, key)).isTrue();
      assertThat(state.exists(TENANT_1, key + 1)).isFalse();
      assertThat(state.exists(TENANT_2, key)).isFalse();
      assertThat(state.exists(DEFAULT_TENANT, key)).isFalse();
    }

    @Test
    void shouldPutSubscriptionWithCorrectScopeKey() {
      // given
      final ConditionalSubscriptionRecord subscription =
          createSubscription(2L, 100L, DEFAULT_TENANT);

      // when
      state.put(1L, subscription);

      // then
      final List<ConditionalSubscription> visitedSubscriptions = new ArrayList<>();
      state.visitByScopeKey(
          subscription.getScopeKey(),
          sub -> {
            visitedSubscriptions.add(sub);
            return true;
          });

      assertThat(visitedSubscriptions).hasSize(1);
      assertThat(visitedSubscriptions.getFirst().getKey()).isEqualTo(1L);
      assertThat(visitedSubscriptions.getFirst().getRecord()).isEqualTo(subscription);
    }

    @Test
    void shouldPutMultipleSubscriptionsForSameScope() {
      // given
      final long scopeKey1 = 500L;
      final long scopeKey2 = 600L;
      final ConditionalSubscriptionRecord subscription1Scope1 =
          createSubscription(scopeKey1, 100L, DEFAULT_TENANT);
      final ConditionalSubscriptionRecord subscription2Scope1 =
          createSubscription(scopeKey1, 100L, DEFAULT_TENANT);
      final ConditionalSubscriptionRecord subscription1Scope2 =
          createSubscription(scopeKey2, 100L, DEFAULT_TENANT);

      // when
      state.put(1L, subscription1Scope1);
      state.put(2L, subscription2Scope1);
      state.put(3L, subscription1Scope2);

      // then
      final List<Long> visitedKeysScope1 = new ArrayList<>();
      state.visitByScopeKey(
          scopeKey1,
          subscription -> {
            visitedKeysScope1.add(subscription.getKey());
            return true;
          });
      final List<Long> visitedKeysScope2 = new ArrayList<>();
      state.visitByScopeKey(
          scopeKey2,
          subscription -> {
            visitedKeysScope2.add(subscription.getKey());
            return true;
          });

      assertThat(visitedKeysScope1).containsExactlyInAnyOrder(1L, 2L);
      assertThat(visitedKeysScope2).containsExactly(3L);
    }

    @Test
    void shouldPutWithoutInsertIntoProcessDefinitionKeyColumnFamily() {
      // given
      final long processDefinitionKey = 100L;
      final ConditionalSubscriptionRecord subscription =
          createSubscription(500L, processDefinitionKey, DEFAULT_TENANT);

      // when
      state.put(1L, subscription);

      // then
      final List<Long> visitedKeys = new ArrayList<>();
      state.visitStartEventSubscriptionsByProcessDefinitionKey(
          processDefinitionKey,
          sub -> {
            visitedKeys.add(sub.getKey());
            return true;
          });

      assertThat(visitedKeys).isEmpty();
    }

    @Test
    void shouldPutAndTrackProcessDefinitionAfter() {
      // given
      final long processDefinitionKey = 100L;
      final ConditionalSubscriptionRecord subscription =
          createSubscription(500L, processDefinitionKey, DEFAULT_TENANT);

      // when
      state.put(1L, subscription);

      // then
      assertThat(state.exists(processDefinitionKey)).isTrue();
    }
  }

  @Nested
  class PutStartTests {

    @Test
    void shouldExistAfterPutStart() {
      // given
      final ConditionalSubscriptionRecord subscription =
          createStartEventSubscription(100L, DEFAULT_TENANT);

      // when
      state.putStart(1L, subscription);

      // then
      assertThat(state.exists(DEFAULT_TENANT, 1L)).isTrue();
    }

    @Test
    void shouldInsertIntoProcessDefinitionKeyColumnFamilyAndNotScopeKeyColumnFamily() {
      // given
      final long processDefinitionKey = 100L;
      final ConditionalSubscriptionRecord subscription =
          createStartEventSubscription(processDefinitionKey, DEFAULT_TENANT);

      // when
      state.putStart(1L, subscription);

      // then
      final List<Long> visitedKeysByProcessDefinitionKey = new ArrayList<>();
      state.visitStartEventSubscriptionsByProcessDefinitionKey(
          processDefinitionKey,
          sub -> {
            visitedKeysByProcessDefinitionKey.add(sub.getKey());
            return true;
          });

      final List<Long> visitedKeysByScopeKey = new ArrayList<>();
      state.visitByScopeKey(
          -1L,
          sub -> {
            visitedKeysByScopeKey.add(sub.getKey());
            return true;
          });

      assertThat(visitedKeysByProcessDefinitionKey).containsExactly(1L);
      assertThat(visitedKeysByScopeKey).isEmpty();
    }

    @Test
    void shouldAllowMultipleStartEventsForSameProcess() {
      // given
      final long processDefinitionKey = 100L;
      final ConditionalSubscriptionRecord subscription1 =
          createStartEventSubscription(processDefinitionKey, DEFAULT_TENANT);
      final ConditionalSubscriptionRecord subscription2 =
          createStartEventSubscription(processDefinitionKey, DEFAULT_TENANT);
      final ConditionalSubscriptionRecord subscription3 =
          createStartEventSubscription(processDefinitionKey, DEFAULT_TENANT);

      // when
      state.putStart(1L, subscription1);
      state.putStart(2L, subscription2);
      state.putStart(3L, subscription3);

      // then
      final List<Long> visitedKeys = new ArrayList<>();
      state.visitStartEventSubscriptionsByProcessDefinitionKey(
          processDefinitionKey,
          sub -> {
            visitedKeys.add(sub.getKey());
            return true;
          });

      assertThat(visitedKeys).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void shouldIsolateStartEventsByTenant() {
      // given
      final long processDefinitionKey = 100L;
      state.putStart(1L, createStartEventSubscription(processDefinitionKey, TENANT_1));
      state.putStart(2L, createStartEventSubscription(processDefinitionKey, TENANT_2));
      state.putStart(3L, createStartEventSubscription(processDefinitionKey, TENANT_1));

      // when
      final List<Long> tenant1Keys = new ArrayList<>();
      state.visitStartEventSubscriptionsByTenantId(
          TENANT_1,
          sub -> {
            tenant1Keys.add(sub.getKey());
            return true;
          });

      final List<Long> tenant2Keys = new ArrayList<>();
      state.visitStartEventSubscriptionsByTenantId(
          TENANT_2,
          sub -> {
            tenant2Keys.add(sub.getKey());
            return true;
          });

      // then
      assertThat(tenant1Keys).containsExactlyInAnyOrder(1L, 3L);
      assertThat(tenant2Keys).containsExactly(2L);
    }

    @Test
    void shouldPutStartWithoutIncrementingProcessDefinitionCount() {
      // given
      final long processDefinitionKey = 100L;
      final ConditionalSubscriptionRecord subscription =
          createStartEventSubscription(processDefinitionKey, DEFAULT_TENANT);

      // when
      state.putStart(1L, subscription);

      // then
      assertThat(state.exists(processDefinitionKey)).isFalse();
    }
  }

  @Nested
  class DeleteTests {

    @Test
    void shouldNotExistAfterDelete() {
      // given
      final var scopeKey = 100L;
      final ConditionalSubscriptionRecord subscription =
          createSubscription(scopeKey, 1L, DEFAULT_TENANT);
      state.put(1L, subscription);
      assertThat(state.exists(DEFAULT_TENANT, 1L)).isTrue();
      final List<Long> keysBeforeDelete = new ArrayList<>();
      state.visitByScopeKey(
          scopeKey,
          sub -> {
            keysBeforeDelete.add(sub.getKey());
            return true;
          });
      assertThat(keysBeforeDelete).containsExactly(1L);
      // when
      state.delete(1L, subscription);

      // then
      assertThat(state.exists(DEFAULT_TENANT, 1L)).isFalse();
      final List<Long> keysAfterDelete = new ArrayList<>();
      state.visitByScopeKey(
          scopeKey,
          sub -> {
            keysAfterDelete.add(sub.getKey());
            return true;
          });
      assertThat(keysAfterDelete).isEmpty();
    }

    @Test
    void shouldDecrementProcessDefinitionCount() {
      // given
      final long processDefinitionKey = 100L;
      final ConditionalSubscriptionRecord subscription1 =
          createSubscription(1L, processDefinitionKey, DEFAULT_TENANT);
      final ConditionalSubscriptionRecord subscription2 =
          createSubscription(2L, processDefinitionKey, DEFAULT_TENANT);

      state.put(1L, subscription1);
      state.put(2L, subscription2);
      assertThat(state.exists(processDefinitionKey)).isTrue();

      // when
      state.delete(1L, subscription1);

      // then
      assertThat(state.exists(processDefinitionKey)).isTrue();

      // when
      state.delete(2L, subscription2);

      // then
      assertThat(state.exists(processDefinitionKey)).isFalse();
    }

    @Test
    void shouldOnlyDeleteSpecifiedSubscription() {
      // given
      final long scopeKey = 500L;
      final ConditionalSubscriptionRecord subscription1 =
          createSubscription(scopeKey, 100L, DEFAULT_TENANT);
      final ConditionalSubscriptionRecord subscription2 =
          createSubscription(scopeKey, 100L, DEFAULT_TENANT);

      state.put(1L, subscription1);
      state.put(2L, subscription2);

      // when - delete only the first one
      state.delete(1L, subscription1);

      // then - second subscription still exists
      assertThat(state.exists(DEFAULT_TENANT, 2L)).isTrue();

      final List<Long> visitedKeys = new ArrayList<>();
      state.visitByScopeKey(
          scopeKey,
          sub -> {
            visitedKeys.add(sub.getKey());
            return true;
          });
      assertThat(visitedKeys).containsExactly(2L);
    }
  }

  @Nested
  class DeleteStartTests {

    @Test
    void shouldNotExistAfterDeleteStart() {
      // given
      final var processDefinitionKey = 100L;
      final ConditionalSubscriptionRecord subscription =
          createStartEventSubscription(processDefinitionKey, DEFAULT_TENANT);
      state.putStart(1L, subscription);
      assertThat(state.exists(DEFAULT_TENANT, 1L)).isTrue();

      // when
      state.deleteStart(1L, subscription);

      // then
      assertThat(state.exists(DEFAULT_TENANT, 1L)).isFalse();

      final List<Long> keysAfterDelete = new ArrayList<>();
      state.visitStartEventSubscriptionsByProcessDefinitionKey(
          processDefinitionKey,
          sub -> {
            keysAfterDelete.add(sub.getKey());
            return true;
          });
      assertThat(keysAfterDelete).isEmpty();
    }

    @Test
    void shouldOnlyDeleteSpecifiedStartEventSubscription() {
      // given
      final long processDefinitionKey = 100L;
      final ConditionalSubscriptionRecord subscription1 =
          createStartEventSubscription(processDefinitionKey, DEFAULT_TENANT);
      final ConditionalSubscriptionRecord subscription2 =
          createStartEventSubscription(processDefinitionKey, DEFAULT_TENANT);

      state.putStart(1L, subscription1);
      state.putStart(2L, subscription2);

      // when
      state.deleteStart(1L, subscription1);

      // then
      assertThat(state.exists(DEFAULT_TENANT, 2L)).isTrue();

      final List<Long> visitedKeys = new ArrayList<>();
      state.visitStartEventSubscriptionsByProcessDefinitionKey(
          processDefinitionKey,
          sub -> {
            visitedKeys.add(sub.getKey());
            return true;
          });
      assertThat(visitedKeys).containsExactly(2L);
    }
  }

  @Nested
  class VisitByScopeKeyTests {

    @Test
    void shouldStopIterationWhenVisitorReturnsFalse() {
      // given
      final long scopeKey = 500L;
      state.put(1L, createSubscription(scopeKey, 100L, DEFAULT_TENANT));
      state.put(2L, createSubscription(scopeKey, 100L, DEFAULT_TENANT));
      state.put(3L, createSubscription(scopeKey, 100L, DEFAULT_TENANT));

      // when
      final List<Long> visitedKeys = new ArrayList<>();
      state.visitByScopeKey(
          scopeKey,
          subscription -> {
            visitedKeys.add(subscription.getKey());
            return false; // stop iteration
          });

      // then
      assertThat(visitedKeys).hasSize(1);
      assertThat(visitedKeys.getFirst()).isIn(1L, 2L, 3L);
    }

    @Test
    void shouldVisitNothingWhenNoSubscriptionsExist() {
      // when
      final List<Long> visitedKeys = new ArrayList<>();
      state.visitByScopeKey(
          999L,
          subscription -> {
            visitedKeys.add(subscription.getKey());
            return true;
          });

      // then
      assertThat(visitedKeys).isEmpty();
    }
  }

  @Nested
  class VisitStartEventSubscriptionsByTenantIdTest {
    @Test
    public void shouldVisitStartEventSubscriptionsByTenantId() {
      // given
      state.putStart(1L, createStartEventSubscription(1L, TENANT_1));
      state.putStart(2L, createStartEventSubscription(2L, TENANT_1));
      state.putStart(3L, createStartEventSubscription(3L, TENANT_1));
      state.putStart(4L, createStartEventSubscription(4L, TENANT_2));
      state.putStart(5L, createStartEventSubscription(5L, TENANT_2));

      // when
      final List<Long> keysForTenant1 = new ArrayList<>();
      state.visitStartEventSubscriptionsByTenantId(
          TENANT_1,
          subscription -> {
            keysForTenant1.add(subscription.getKey());
            return true;
          });

      final List<Long> keysForTenant2 = new ArrayList<>();
      state.visitStartEventSubscriptionsByTenantId(
          TENANT_2,
          subscription -> {
            keysForTenant2.add(subscription.getKey());
            return true;
          });

      // then
      assertThat(keysForTenant1).containsExactlyInAnyOrder(1L, 2L, 3L);
      assertThat(keysForTenant2).containsExactlyInAnyOrder(4L, 5L);
    }

    @Test
    public void shouldVisitStartEventSubscriptionsByTenantIdForMultipleProcesses() {
      // given
      state.putStart(1L, createStartEventSubscription(1L, DEFAULT_TENANT));
      state.putStart(2L, createStartEventSubscription(1L, DEFAULT_TENANT));
      state.putStart(3L, createStartEventSubscription(2L, DEFAULT_TENANT));

      // when
      final List<Long> visitedSubscriptionKeys = new ArrayList<>();
      state.visitStartEventSubscriptionsByTenantId(
          DEFAULT_TENANT,
          subscription -> {
            visitedSubscriptionKeys.add(subscription.getKey());
            return true;
          });

      // then
      assertThat(visitedSubscriptionKeys).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    public void shouldStopIterationWhenVisitorReturnsFalse() {
      // given
      state.putStart(1L, createStartEventSubscription(1L, DEFAULT_TENANT));
      state.putStart(2L, createStartEventSubscription(2L, DEFAULT_TENANT));
      state.putStart(3L, createStartEventSubscription(3L, DEFAULT_TENANT));

      // when
      final List<Long> visitedKeys = new ArrayList<>();
      state.visitStartEventSubscriptionsByTenantId(
          DEFAULT_TENANT,
          subscription -> {
            visitedKeys.add(subscription.getKey());
            return false;
          });

      // then
      assertThat(visitedKeys).hasSize(1);
      assertThat(visitedKeys.getFirst()).isIn(1L, 2L, 3L);
    }

    @Test
    public void shouldVisitNothingWhenNoSubscriptionsExist() {
      // when
      final List<Long> visitedKeys = new ArrayList<>();
      state.visitStartEventSubscriptionsByTenantId(
          DEFAULT_TENANT,
          subscription -> {
            visitedKeys.add(subscription.getKey());
            return true;
          });

      // then
      assertThat(visitedKeys).isEmpty();
    }
  }
}
