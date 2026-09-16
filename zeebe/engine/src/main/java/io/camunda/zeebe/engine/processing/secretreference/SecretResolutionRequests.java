/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import java.util.Collection;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the {@link SecretReferenceRecord} that a {@code RESOLUTION_REQUESTED} event carries. The
 * three producers of that event — the pull and push activation paths and the incident resolve path
 * — all address a reference by its store and name and attach the keys of the jobs waiting on it, so
 * the construction lives here rather than being repeated, subtly differently, at each.
 */
@NullMarked
public final class SecretResolutionRequests {

  private SecretResolutionRequests() {}

  /** A request for {@code reference} carrying the single {@code jobKey} waiting on it. */
  public static SecretReferenceRecord requestFor(
      final SecretReference reference, final long jobKey) {
    return newRequest(reference).addJobKey(jobKey);
  }

  /** A request for {@code reference} carrying every {@code jobKey} waiting on it. */
  public static SecretReferenceRecord requestFor(
      final SecretReference reference, final Collection<Long> jobKeys) {
    final var request = newRequest(reference);
    jobKeys.forEach(request::addJobKey);
    return request;
  }

  private static SecretReferenceRecord newRequest(final SecretReference reference) {
    return new SecretReferenceRecord()
        .setStoreId(reference.storeId())
        .setSecretReference(reference.name());
  }
}
