/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p>Read off {@link IdentityConfiguration} rather than the property, because that is the value the
 * SDK will actually send. The two can disagree: the SDK binds with relaxed rules and so honours
 * {@code CAMUNDA_IDENTITY_COMPONENT_ID}, while a plain placeholder lookup does not, which would
 * leave this reporting a component the SDK is not sending.
 *
 * <p>Nothing is sent at all on the Keycloak type. There the SDK reads permissions out of the
 * token's own {@code permissions} claim, keyed by audience, and never calls Identity, so component
 * scoping does not reach that setup however this is configured.
 */
@Component
@Conditional(CCSMCondition.class)
public class CCSMIdentityComponentIdReporter {

  private static final Logger LOG = LoggerFactory.getLogger(CCSMIdentityComponentIdReporter.class);

  private final IdentityConfiguration identityConfiguration;

  public CCSMIdentityComponentIdReporter(final IdentityConfiguration identityConfiguration) {
    this.identityConfiguration = identityConfiguration;
  }

  @PostConstruct
  public void report() {
    if (identityConfiguration.getType() == IdentityConfiguration.Type.KEYCLOAK) {
      LOG.info(
          "Permissions come from the token's own claim on the Keycloak identity type, so Optimize"
              + " does not name a component to Management Identity and component scoping does not"
              + " apply.");
      return;
    }

    final String componentId = identityConfiguration.getComponentId();

    if (componentId == null || componentId.isBlank()) {
      LOG.warn(
          "No component id is set, so Optimize does not name itself to Management Identity."
              + " Identity will return every permission on Optimize's audience rather than only"
              + " the ones Optimize's own resource server defines. Set"
              + " camunda.identity.componentId to the key of Optimize's entry under Identity's"
              + " component-presets.");
      return;
    }

    LOG.info("Naming this component '{}' to Management Identity.", componentId);
  }
}
