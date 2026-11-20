/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.hub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.Member;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.engine.processing.policy.PolicyStatus;
import java.io.UncheckedIOException;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * Where the policy state of a physical tenant is gossiped: one member property per tenant, set by
 * the broker that leads partition 1 of the tenant. SWIM spreads a member's properties to every
 * other member, so the value must stay small.
 */
@NullMarked
public final class PolicyStateGossip {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final String KEY_PREFIX = "policyState:";
  private static final ObjectMapper MAPPER =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private PolicyStateGossip() {}

  /** The member property that holds the policy state of the physical tenant. */
  public static String key(final String physicalTenantId) {
    return KEY_PREFIX + physicalTenantId;
  }

  static String encode(final PolicyStatus status) {
    try {
      return MAPPER.writeValueAsString(status);
    } catch (final JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The policy state of the tenant that the member gossips, if it gossips one. */
  public static Optional<PolicyStatus> read(final Member member, final String physicalTenantId) {
    final var value = member.properties().getProperty(key(physicalTenantId));
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(MAPPER.readValue(value, PolicyStatus.class));
    } catch (final JsonProcessingException e) {
      LOG.warn(
          "Ignoring the policy state of physical tenant {} gossiped by {}: {}",
          physicalTenantId,
          member.id(),
          value,
          e);
      return Optional.empty();
    }
  }
}
