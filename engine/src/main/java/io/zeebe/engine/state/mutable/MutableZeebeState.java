/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.mutable;

import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.immutable.ImmutableZeebeState;
import java.util.function.BiConsumer;

public interface MutableZeebeState extends ImmutableZeebeState {

  @Override
  MutableDeploymentState getDeploymentState();

  @Override
  MutableProcessState getProcessState();

  @Override
  MutableJobState getJobState();

  @Override
  MutableMessageState getMessageState();

  @Override
  MutableMessageSubscriptionState getMessageSubscriptionState();

  @Override
  MutableMessageStartEventSubscriptionState getMessageStartEventSubscriptionState();

  @Override
  MutableProcessInstanceSubscriptionState getProcessInstanceSubscriptionState();

  @Override
  MutableIncidentState getIncidentState();

  @Override
  MutableBlackListState getBlackListState();

  @Override
  MutableVariableState getVariableState();

  @Override
  MutableTimerInstanceState getTimerState();

  @Override
  MutableElementInstanceState getElementInstanceState();

  @Override
  MutableEventScopeInstanceState getEventScopeInstanceState();

  KeyGenerator getKeyGenerator();

  /**
   * Iterates over all entries for a given column family and presents each entry to the consumer.
   *
   * <p><strong>Hint</strong> Should only be used in tests.
   *
   * @param columnFamily the enum instance of the column family
   * @param keyInstance this instance defines the type of the column family key type
   * @param valueInstance this instance defines the type of the column family value type
   * @param visitor the visitor that will be called for each entry
   * @param <KeyType> the key type of the column family
   * @param <ValueType> the value type of the column family
   */
  <KeyType extends DbKey, ValueType extends DbValue> void forEach(
      final ZbColumnFamilies columnFamily,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final BiConsumer<KeyType, ValueType> visitor);
}
