/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.SecretReferenceState;

public interface MutableSecretReferenceState extends SecretReferenceState {

  void addPendingSecretReference(String storeId, String secretReference);

  void removePendingSecretReference(String storeId, String secretReference);

  /** Writes to both waiting-job indexes. Always writes both together. */
  void addWaitingJob(String storeId, String secretReference, long jobKey);

  /** Removes from both waiting-job indexes. Always removes both together. */
  void removeWaitingJob(String storeId, String secretReference, long jobKey);
}
