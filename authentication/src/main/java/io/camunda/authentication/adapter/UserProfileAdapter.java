/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.adapter;

import io.camunda.gatekeeper.model.identity.UserProfile;
import io.camunda.gatekeeper.spi.UserProfileProvider;
import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.service.UserServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class UserProfileAdapter implements UserProfileProvider {

  private static final Logger LOG = LoggerFactory.getLogger(UserProfileAdapter.class);

  private final UserServices userServices;

  public UserProfileAdapter(final UserServices userServices) {
    this.userServices = userServices;
  }

  @Override
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
}
