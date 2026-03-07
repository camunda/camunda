/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.spi;

import io.camunda.auth.domain.model.UserProfile;

/**
 * SPI for resolving user profile information (display name, email) from a user identifier.
 * Implementations typically query a user database.
 */
public interface UserProfileProvider {

  /** Resolves the profile for the given username. Returns null if not found. */
  UserProfile getUserProfile(String username);
}
