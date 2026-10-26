/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Says once, at startup, which component Optimize names itself as when it asks Management Identity
 * for a token's permissions.
 *
 * <p>Worth stating out loud because the failure is otherwise invisible. Identity treats a missing
 * or unrecognised component as a caller that named none and returns every permission on the
 * audience, exactly as it did before component scoping existed. Nothing errors, so a deployment
 * that has blanked this out looks healthy while being unscoped.
 */
@Component
@Conditional(CCSMCondition.class)
public class CCSMIdentityComponentIdReporter {

  private static final Logger LOG = LoggerFactory.getLogger(CCSMIdentityComponentIdReporter.class);

  private final String componentId;

  public CCSMIdentityComponentIdReporter(
      @Value("${camunda.identity.componentId:}") final String componentId) {
    this.componentId = componentId;
  }

  @PostConstruct
  public void report() {
    if (componentId == null || componentId.isBlank()) {
      LOG.warn(
          "No camunda.identity.componentId is set, so Optimize does not name itself to Management"
              + " Identity. Identity will return every permission on Optimize's audience rather"
              + " than only the ones Optimize's own resource server defines. Set it to the key of"
              + " Optimize's entry under Identity's component-presets.");
      return;
    }

    LOG.info("Naming this component '{}' to Management Identity.", componentId);
  }
}
