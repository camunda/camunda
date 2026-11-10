/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api;

/**
 * Identifies one gateway process. A gateway holds the connections of its callers, so a response
 * must return to the gateway that accepted the request.
 *
 * @param value a non-blank identifier that is unique among all gateways of the cluster
 */
public record GatewayId(String value) {

  public GatewayId {
    if (value.isBlank()) {
      throw new IllegalArgumentException("gateway id must not be blank");
    }
  }
}
