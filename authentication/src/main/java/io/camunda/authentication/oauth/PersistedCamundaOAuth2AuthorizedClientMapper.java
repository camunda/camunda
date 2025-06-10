/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oauth;

import io.camunda.search.entities.PersistentOAuth2AuthorizedClientEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

public class PersistedCamundaOAuth2AuthorizedClientMapper {

  public PersistentOAuth2AuthorizedClientEntity toPersistentOAuth2AuthorizedClientEntity(
      final OAuth2AuthorizedClient authorizedClient) {
    return null;
  }

  public <T extends OAuth2AuthorizedClient> T toOAuth2AuthorizedClient(
      final PersistentOAuth2AuthorizedClientEntity storedClientEntity) {
    return null;
  }
}
