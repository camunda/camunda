/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;

public interface SecretReferenceState {

  /** Returns true if the secret is pending resolution. */
  boolean isPending(String storeId, String secretReference);

  /** Returns true if the job is still waiting for the given (storeId, secretReference) pair. */
  boolean isWaiting(String storeId, String secretReference, long jobKey);

  /**
   * Visits all job keys waiting for the given (storeId, secretReference) pair. The visitor receives
   * each jobKey and returns {@code true} to continue iteration or {@code false} to stop early.
   */
  void visitJobsBySecretReference(String storeId, String secretReference, LongPredicate visitor);

  /**
   * Visits all (storeId, secretReference) pairs that the given job is waiting for. The visitor
   * receives (storeId, secretReference) and returns {@code true} to continue iteration or {@code
   * false} to stop early.
   */
  void visitSecretReferencesByJob(long jobKey, BiPredicate<String, String> visitor);

  /**
   * Visits pending (storeId, secretReference) entries starting at {@code startAt} (inclusive, if it
   * still exists) or from the beginning if {@code startAt} is {@code null}. The visitor returns
   * {@code true} to continue or {@code false} to stop early.
   *
   * @return the cursor identifying where iteration stopped, to pass as {@code startAt} on the next
   *     call to resume fairly; {@code null} if the visitor was never told to stop (i.e. iteration
   *     reached the end of the column family)
   */
  PendingRefCursor visitPendingSecretReferences(
      PendingRefCursor startAt, BiPredicate<String, String> visitor);

  /**
   * Collects all (storeId, secretReference) pairs that the given job is waiting for, via {@link
   * #visitSecretReferencesByJob}.
   */
  default List<Map.Entry<String, String>> collectSecretReferencesByJob(final long jobKey) {
    final List<Map.Entry<String, String>> refs = new ArrayList<>();
    visitSecretReferencesByJob(
        jobKey,
        (storeId, secretReference) -> {
          refs.add(Map.entry(storeId, secretReference));
          return true;
        });
    return refs;
  }

  /** Resume point for {@link #visitPendingSecretReferences}, identifying the last-visited entry. */
  record PendingRefCursor(String storeId, String secretReference) {}
}
