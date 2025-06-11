/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.clients.core.SearchDeleteRequest;
import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.entities.PersistentOAuth2AuthorizedClientEntity;
import io.camunda.webapps.schema.descriptors.index.PersistentAuthorizedClientIndexDescriptor;

public class PersistentOAuth2AuthorizedClientsClientImpl
    implements PersistentOAuth2AuthorizedClientsClient {

  private final DocumentBasedSearchClient readClient;
  private final DocumentBasedWriteClient writeClient;
  private final PersistentAuthorizedClientIndexDescriptor persistentAuthorizedClientIndex;

  public PersistentOAuth2AuthorizedClientsClientImpl(
      final DocumentBasedSearchClient readClient,
      final DocumentBasedWriteClient writeClient,
      final PersistentAuthorizedClientIndexDescriptor persistentAuthorizedClientIndex) {
    this.readClient = readClient;
    this.writeClient = writeClient;
    this.persistentAuthorizedClientIndex = persistentAuthorizedClientIndex;
  }

  @Override
  public PersistentOAuth2AuthorizedClientEntity loadAuthorizedClient(
      final String clientRegistrationId, final String principalName) {
    final var request =
        SearchGetRequest.of(
            b ->
                b.id(getAuthorizedClientRecordId(clientRegistrationId, principalName))
                    .index(persistentAuthorizedClientIndex.getFullQualifiedName()));
    final var authorizedClient =
        readClient.get(request, PersistentOAuth2AuthorizedClientEntity.class);
    return authorizedClient.source();
  }

  @Override
  public void saveAuthorizedClient(
      final PersistentOAuth2AuthorizedClientEntity authorizedClient, final String principalName) {
    writeClient.index(
        SearchIndexRequest.of(
            b ->
                b.id(
                        getAuthorizedClientRecordId(
                            authorizedClient.clientRegistrationId(), principalName))
                    .index(persistentAuthorizedClientIndex.getFullQualifiedName())
                    .document(authorizedClient)));
  }

  @Override
  public void removeAuthorizedClient(
      final String clientRegistrationId, final String principalName) {
    writeClient.delete(
        SearchDeleteRequest.of(
            b ->
                b.id(getAuthorizedClientRecordId(clientRegistrationId, principalName))
                    .index(persistentAuthorizedClientIndex.getFullQualifiedName())));
  }

  private String getAuthorizedClientRecordId(
      final String clientRegistrationId, final String principalName) {
    return clientRegistrationId + ":" + principalName;
  }
}
