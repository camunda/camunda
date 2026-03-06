/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.UserProfile;
import io.camunda.auth.domain.spi.UserProfileProvider;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.service.UserServices;
import java.util.Objects;

/**
 * Resolves user profile information (display name, email) by querying the user search index.
 * Returns null if the user is not found.
 */
public class CamundaUserProfileProvider implements UserProfileProvider {

  private final UserServices userServices;

  public CamundaUserProfileProvider(final UserServices userServices) {
    this.userServices = userServices;
  }

  @Override
  public UserProfile getUserProfile(final String username) {
    final var query =
        SearchQueryBuilders.userSearchQuery(
            fn -> fn.filter(f -> f.usernames(username)).page(p -> p.size(1)));
    return userServices
        .withAuthentication(CamundaAuthentication.anonymous())
        .search(query)
        .items()
        .stream()
        .filter(Objects::nonNull)
        .findFirst()
        .map(user -> new UserProfile(user.name(), user.email()))
        .orElse(null);
  }
}
