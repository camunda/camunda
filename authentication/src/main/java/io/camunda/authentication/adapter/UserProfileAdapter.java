/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.adapter;

import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.service.UserServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class UserProfileAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(UserProfileAdapter.class);

  private final UserServices userServices;

  public UserProfileAdapter(final UserServices userServices) {
    this.userServices = userServices;
  }

  /** Returns the display name and email for the given username, or null if not found. */
  public UserProfile getUserProfile(final String username) {
    try {
      final var user = userServices.getUser(username, CamundaAuthentication.anonymous());
      if (user == null) {
        return null;
      }
      return new UserProfile(user.name(), user.email());
    } catch (final RuntimeException ex) {
      LOG.warn("Failed to resolve user profile for username: {}", username, ex);
      return null;
    }
  }

  /** Simple record holding user display name and email. */
  public record UserProfile(String displayName, String email) {}
}
