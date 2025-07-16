/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Fallback UserDetailsService that is used when secondary storage is not available.
 * This service always fails authentication attempts with a clear error message.
 */
public class NoSecondaryStorageUserDetailsService implements UserDetailsService {

  private static final Logger LOG = LoggerFactory.getLogger(NoSecondaryStorageUserDetailsService.class);

  @Override
  public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
    LOG.error(
        "Authentication attempted for user '{}' but secondary storage is disabled (camunda.database.type=none). "
            + "User authentication requires secondary storage to be configured.",
        username);
    throw new UsernameNotFoundException(
        "Authentication is not available when secondary storage is disabled (camunda.database.type=none). "
            + "Please configure secondary storage to enable authentication.");
  }
}