/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

public interface SecretReferenceState {

  /** Returns true if the secret is pending resolution. */
  boolean isPending(String storeId, String secretReference);

  /**
   * Visits all job keys waiting for the given (storeId, secretReference) pair. The visitor receives
   * each jobKey.
   */
  void visitJobsBySecretReference(String storeId, String secretReference, LongConsumer visitor);

  /**
   * Visits all (storeId, secretReference) pairs that the given job is waiting for. The visitor
   * receives (storeId, secretReference).
   */
  void visitSecretReferencesByJob(long jobKey, BiConsumer<String, String> visitor);
}
