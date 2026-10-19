/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.fanout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.api.model.policy.PolicyChangeSet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Maps identity fan-out events onto the CSL {@code PolicyProjectionPort}. */
@Component
@ConditionalOnProperty(prefix = "camunda.hub.fanout", name = "enabled", havingValue = "true")
public class IdentityPolicyChangeSetHandler implements FanoutEventHandler {

  public static final String TYPE = "io.camunda.identity.policy-change-set.v1";

  // Plain mapper: the CSL model round-trips through an unconfigured ObjectMapper by contract.
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final PolicyProjectionPorts ports;

  public IdentityPolicyChangeSetHandler(final PolicyProjectionPorts ports) {
    this.ports = ports;
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public void handle(final String physicalTenantId, final JsonNode data) throws Exception {
    final var changeSet = MAPPER.treeToValue(data, PolicyChangeSet.class);
    ports.forPhysicalTenant(physicalTenantId).apply(changeSet);
  }
}
