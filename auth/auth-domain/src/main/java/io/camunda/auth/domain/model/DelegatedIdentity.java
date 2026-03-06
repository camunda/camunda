/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.model;

import java.util.Objects;

/**
 * Represents the identity chain in an on-behalf-of flow. Tracks the original subject and the actor
 * performing the delegation, with depth tracking for chain validation.
 *
 * <p>In JWT terms, the subject is the {@code sub} claim and the actor is represented by the nested
 * {@code act} claim (RFC 8693 Section 4.1).
 *
 * @param subjectId the principal ID of the original subject (the user being acted on behalf of)
 * @param actorId the principal ID of the acting party (the service/client performing the action)
 * @param chainDepth the current depth of the delegation chain (1 = direct delegation)
 * @param parent the parent identity in the delegation chain, or null if this is the root
 */
public record DelegatedIdentity(
    String subjectId, String actorId, int chainDepth, DelegatedIdentity parent) {

  public DelegatedIdentity {
    Objects.requireNonNull(subjectId, "subjectId must not be null");
    Objects.requireNonNull(actorId, "actorId must not be null");
    if (chainDepth < 1) {
      throw new IllegalArgumentException("chainDepth must be >= 1, was: " + chainDepth);
    }
  }

  public static DelegatedIdentity direct(final String subjectId, final String actorId) {
    return new DelegatedIdentity(subjectId, actorId, 1, null);
  }

  public DelegatedIdentity delegateTo(final String newActorId) {
    return new DelegatedIdentity(subjectId, newActorId, chainDepth + 1, this);
  }
}
