/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.core.SearchDeleteRequest;
import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.core.SearchGetResponse;
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.entities.PersistentOAuth2AuthorizedClientEntity;
import io.camunda.webapps.schema.descriptors.index.PersistentAuthorizedClientIndexDescriptor;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersistentOAuth2AuthorizedClientsClientImplTest {

  private static final String CLIENT_REGISTRATION_ID = "test-client";
  private static final String PRINCIPAL_NAME = "test-user";
  private static final String INDEX_NAME = "persistent-authorized-clients";
  private static final String EXPECTED_RECORD_ID = CLIENT_REGISTRATION_ID + ":" + PRINCIPAL_NAME;

  @Mock private DocumentBasedSearchClient readClient;
  @Mock private DocumentBasedWriteClient writeClient;
  @Mock private PersistentAuthorizedClientIndexDescriptor persistentAuthorizedClientIndex;
  @Mock private SearchGetResponse<PersistentOAuth2AuthorizedClientEntity> searchResponse;
  @Captor private ArgumentCaptor<SearchGetRequest> getRequestCaptor;
  @Captor private ArgumentCaptor<SearchIndexRequest> indexRequestCaptor;
  @Captor private ArgumentCaptor<SearchDeleteRequest> deleteRequestCaptor;

  private PersistentOAuth2AuthorizedClientsClientImpl client;

  @BeforeEach
  void setUp() {
    when(persistentAuthorizedClientIndex.getFullQualifiedName()).thenReturn(INDEX_NAME);

    client =
        new PersistentOAuth2AuthorizedClientsClientImpl(
            readClient, writeClient, persistentAuthorizedClientIndex);
  }

  @Test
  void shouldLoadAuthorizedClientSuccessfully() {
    // Given
    final PersistentOAuth2AuthorizedClientEntity expectedEntity = createTestEntity();
    when(readClient.get(
            any(SearchGetRequest.class), eq(PersistentOAuth2AuthorizedClientEntity.class)))
        .thenReturn(searchResponse);
    when(searchResponse.source()).thenReturn(expectedEntity);

    // When
    final PersistentOAuth2AuthorizedClientEntity result =
        client.loadAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME);

    // Then
    assertThat(result).isEqualTo(expectedEntity);

    verify(readClient)
        .get(getRequestCaptor.capture(), eq(PersistentOAuth2AuthorizedClientEntity.class));
    final SearchGetRequest capturedRequest = getRequestCaptor.getValue();
    assertThat(capturedRequest.id()).isEqualTo(EXPECTED_RECORD_ID);
    assertThat(capturedRequest.index()).isEqualTo(INDEX_NAME);
  }

  @Test
  void shouldSaveAuthorizedClientSuccessfully() {
    // Given
    final PersistentOAuth2AuthorizedClientEntity entity = createTestEntity();

    // When
    client.saveAuthorizedClient(entity, PRINCIPAL_NAME);

    // Then
    verify(writeClient).index(indexRequestCaptor.capture());
    final SearchIndexRequest capturedRequest = indexRequestCaptor.getValue();
    assertThat(capturedRequest.id()).isEqualTo(EXPECTED_RECORD_ID);
    assertThat(capturedRequest.index()).isEqualTo(INDEX_NAME);
    assertThat(capturedRequest.document()).isEqualTo(entity);
  }

  @Test
  @DisplayName("Should remove authorized client successfully")
  void shouldRemoveAuthorizedClientSuccessfully() {
    // When
    client.removeAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME);

    // Then
    verify(writeClient).delete(deleteRequestCaptor.capture());
    final SearchDeleteRequest capturedRequest = deleteRequestCaptor.getValue();
    assertThat(capturedRequest.id()).isEqualTo(EXPECTED_RECORD_ID);
    assertThat(capturedRequest.index()).isEqualTo(INDEX_NAME);
  }

  @Test
  void shouldGenerateCorrectRecordIdWithSpecialCharacters() {
    // Given
    final String clientRegId = "client-with-special@chars";
    final String principalName = "user.with.dots@domain.com";
    final String expectedId = clientRegId + ":" + principalName;
    final PersistentOAuth2AuthorizedClientEntity entity = createTestEntityWithClientId(clientRegId);

    // When
    client.saveAuthorizedClient(entity, principalName);

    // Then
    verify(writeClient).index(indexRequestCaptor.capture());
    final SearchIndexRequest capturedRequest = indexRequestCaptor.getValue();
    assertThat(capturedRequest.id()).isEqualTo(expectedId);
  }

  @Test
  void shouldUseCorrectIndexNameFromDescriptor() {
    // Given
    final String customIndexName = "custom-authorized-clients-index";
    when(persistentAuthorizedClientIndex.getFullQualifiedName()).thenReturn(customIndexName);

    final PersistentOAuth2AuthorizedClientEntity entity = createTestEntity();
    when(readClient.get(
            any(SearchGetRequest.class), eq(PersistentOAuth2AuthorizedClientEntity.class)))
        .thenReturn(searchResponse);
    when(searchResponse.source()).thenReturn(entity);

    // When
    client.loadAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME);
    client.saveAuthorizedClient(entity, PRINCIPAL_NAME);
    client.removeAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME);

    // Then
    verify(readClient)
        .get(getRequestCaptor.capture(), eq(PersistentOAuth2AuthorizedClientEntity.class));
    assertThat(getRequestCaptor.getValue().index()).isEqualTo(customIndexName);

    verify(writeClient).index(indexRequestCaptor.capture());
    assertThat(indexRequestCaptor.getValue().index()).isEqualTo(customIndexName);

    verify(writeClient).delete(deleteRequestCaptor.capture());
    assertThat(deleteRequestCaptor.getValue().index()).isEqualTo(customIndexName);
  }

  private PersistentOAuth2AuthorizedClientEntity createTestEntity() {
    return createTestEntityWithClientId(CLIENT_REGISTRATION_ID);
  }

  private PersistentOAuth2AuthorizedClientEntity createTestEntityWithClientId(
      final String clientId) {
    return new PersistentOAuth2AuthorizedClientEntity(
        "test-id",
        clientId,
        PRINCIPAL_NAME,
        "access-token-value",
        "Bearer",
        OffsetDateTime.now().minusHours(1),
        OffsetDateTime.now().plusHours(1),
        Set.of("read", "write"),
        "refresh-token-value",
        OffsetDateTime.now().minusHours(1),
        OffsetDateTime.now().plusDays(30));
  }
}
