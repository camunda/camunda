/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state;

import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import io.zeebe.engine.state.mutable.MutableBlackListState;
import io.zeebe.engine.state.mutable.MutableDeploymentState;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableIncidentState;
import io.zeebe.engine.state.mutable.MutableJobState;
import io.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.zeebe.engine.state.mutable.MutableMessageState;
import io.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.zeebe.engine.state.mutable.MutableTimerInstanceState;
import io.zeebe.engine.state.mutable.MutableVariableState;
import io.zeebe.engine.state.mutable.MutableProcessInstanceSubscriptionState;
import io.zeebe.engine.state.mutable.MutableProcessState;
import java.util.function.BiConsumer;

public interface ZeebeState {

  MutableDeploymentState getDeploymentState();

  MutableProcessState getProcessState();

  MutableJobState getJobState();

  MutableMessageState getMessageState();

  MutableMessageSubscriptionState getMessageSubscriptionState();

  MutableMessageStartEventSubscriptionState getMessageStartEventSubscriptionState();

  MutableProcessInstanceSubscriptionState getProcessInstanceSubscriptionState();

  MutableIncidentState getIncidentState();

  KeyGenerator getKeyGenerator();

  MutableBlackListState getBlackListState();

  MutableVariableState getVariableState();

  MutableTimerInstanceState getTimerState();

  MutableElementInstanceState getElementInstanceState();

  MutableEventScopeInstanceState getEventScopeInstanceState();

  int getPartitionId();

  boolean isEmpty(final ZbColumnFamilies column);

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
