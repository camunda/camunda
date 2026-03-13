/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.model.identity;

/**
 * Represents the security context for the current operation, containing the authenticated identity.
 * Consumers that need authorization data should compose this with their own authorization context.
 */
public record SecurityContext(CamundaAuthentication authentication) {

  public static SecurityContext of(final CamundaAuthentication authentication) {
    return new SecurityContext(authentication);
  }
}
