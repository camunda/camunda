/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.entities.PersistentOAuth2AuthorizedClientEntity;
import io.camunda.search.query.SearchQueryResult;

public interface PersistentOAuth2AuthorizedClientsClient {
  PersistentOAuth2AuthorizedClientEntity loadAuthorizedClient(
      String clientRegistrationId, String principalName);

  void saveAuthorizedClient(
      PersistentOAuth2AuthorizedClientEntity authorizedClient, String principalName);

  void removeAuthorizedClient(String clientRegistrationId, String principalName);

  SearchQueryResult<PersistentOAuth2AuthorizedClientEntity>
      getAllPersistentOAuth2AuthorizedClients();

  void removeExpiredAuthorizedClients();
}
