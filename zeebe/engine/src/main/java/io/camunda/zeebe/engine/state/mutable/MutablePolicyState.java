/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.PolicyState;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface MutablePolicyState extends PolicyState {

  /** Commits the given policy version, which replaces the previously committed one. */
  void commit(String policyId, long policyVersion, long seqNo);
}
