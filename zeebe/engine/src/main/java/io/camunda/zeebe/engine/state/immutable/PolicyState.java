/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/** The committed policy that Hub manages, in hub-managed mode. */
@NullMarked
public interface PolicyState {

  /** The policy version without any committed Hub policy, as in local mode. */
  long NO_POLICY_VERSION = 0L;

  /** The committed policy, if a policy update was committed. */
  Optional<CommittedPolicy> getCurrentPolicy();

  /**
   * The version of the current policy, or {@link #NO_POLICY_VERSION} if no policy update was
   * committed.
   */
  long getCurrentPolicyVersion();

  record CommittedPolicy(String policyId, long policyVersion, long seqNo) {}
}
