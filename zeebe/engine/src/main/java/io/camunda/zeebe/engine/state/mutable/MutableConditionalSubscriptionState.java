/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.ConditionalSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalSubscriptionRecord;

public interface MutableConditionalSubscriptionState extends ConditionalSubscriptionState {

  /**
   * Stores a conditional subscription by its subscription key.
   *
   * @param key the key of the subscription
   * @param subscription the subscription record
   */
  void put(final long key, ConditionalSubscriptionRecord subscription);

  /**
   * Migrates an existing conditional subscription by its subscription key.
   *
   * <p>This method is intended to be used during process instance migration to update an existing
   * subscription's process definition key and catch event ID to match the target process
   * definition, while retaining the same subscription key.
   *
   * @param key the key of the subscription
   * @param subscription the subscription record
   */
  void migrate(final long key, ConditionalSubscriptionRecord subscription);

  /**
   * Stores a conditional subscription for a root-level conditional start event (where {@code
   * elementInstanceKey < 0}).
   *
   * <p>This method indexes the subscription by process definition key instead of by scope key,
   * using a different column family than {@link #put(long, ConditionalSubscriptionRecord)}.
   *
   * <p>Use this method for conditional start events to ensure proper subscription lifecycle
   * management during process redeployment.
   *
   * @param key the key of the subscription
   * @param subscription the subscription record
   */
  void putStart(final long key, ConditionalSubscriptionRecord subscription);

  /**
   * Deletes a conditional subscription by its subscription key.
   *
   * @param key the key of the subscription
   * @param subscription the subscription record
   */
  void delete(final long key, ConditionalSubscriptionRecord subscription);

  /**
   * Deletes a conditional subscription for a root-level conditional start event where {@code *
   * elementInstanceKey < 0}).
   *
   * <p>This method removes the subscription from the column family indexed by process definition
   * key, which is different from the one used by {@link #delete(long,
   * ConditionalSubscriptionRecord)}.
   *
   * <p>Use this method for conditional start events to ensure proper subscription lifecycle
   * management during process redeployment.
   *
   * @param key the key of the subscription
   * @param subscription the subscription record
   */
  void deleteStart(long key, ConditionalSubscriptionRecord subscription);
}
