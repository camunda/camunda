/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;

public interface BannedInstanceState extends StreamProcessorLifecycleAware {

  /** Returns true if the process instance with the given key is banned, false otherwise. */
  boolean isProcessInstanceBanned(final long key);

  boolean isBanned(final TypedRecord record);

  /** Returns a list of keys of all banned process instances */
  List<Long> getBannedProcessInstanceKeys();
}
