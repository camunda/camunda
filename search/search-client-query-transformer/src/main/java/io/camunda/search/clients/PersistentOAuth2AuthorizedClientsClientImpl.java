/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.entities.PersistentOAuth2AuthorizedClientEntity;

public class PersistentOAuth2AuthorizedClientsClientImpl
    implements PersistentOAuth2AuthorizedClientsClient {

  @Override
  public PersistentOAuth2AuthorizedClientEntity loadAuthorizedClient(
      final String clientRegistrationId, final String principalName) {
    System.out.println("Not implemented yet");
    return null;
  }

  @Override
  public void saveAuthorizedClient(
      final PersistentOAuth2AuthorizedClientEntity authorizedClient, final String principalName) {
    System.out.println("Not implemented yet");
  }

  @Override
  public void removeAuthorizedClient(
      final String clientRegistrationId, final String principalName) {
    System.out.println("Not implemented yet");
  }
}
