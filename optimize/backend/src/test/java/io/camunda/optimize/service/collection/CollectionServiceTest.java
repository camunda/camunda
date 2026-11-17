/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizationType;
import io.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import io.camunda.optimize.service.db.reader.CollectionReader;
import io.camunda.optimize.service.db.writer.CollectionWriter;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.relations.CollectionRelationService;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.EntityConfiguration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CollectionServiceTest {

  private static final String USER_ID = "demo";
  private static final int CONFIGURED_LIMIT = 256;
  private static final String COLLECTION_ID = "collectionId";

  @Mock private AuthorizedCollectionService authorizedCollectionService;
  @Mock private CollectionRelationService collectionRelationService;
  @Mock private CollectionEntityService collectionEntityService;
  @Mock private CollectionWriter collectionWriter;
  @Mock private CollectionReader collectionReader;
  @Mock private AbstractIdentityService identityService;
  @Mock private ConfigurationService configurationService;

  @InjectMocks private CollectionService collectionService;

  @BeforeEach
  void setUp() {
    final EntityConfiguration entityConfiguration = new EntityConfiguration();
    entityConfiguration.setNameMaxLength(CONFIGURED_LIMIT);
    when(configurationService.getEntityConfiguration()).thenReturn(entityConfiguration);
    when(identityService.getEnabledAuthorizations())
        .thenReturn(List.of(AuthorizationType.ENTITY_EDITOR));
  }

  @Test
  void shouldRejectCreatingCollectionWithNameExceedingConfiguredLimit() {
    // given
    final var request = new PartialCollectionDefinitionRequestDto("a".repeat(CONFIGURED_LIMIT + 1));

    // when
    final var thrown =
        assertThatExceptionOfType(OptimizeValidationException.class)
            .isThrownBy(() -> collectionService.createNewCollectionAndReturnId(USER_ID, request));

    // then
    thrown.withMessage("Collection names cannot be greater than 256 characters");
    verifyNoInteractions(collectionWriter);
  }

  @Test
  void shouldAcceptCreatingCollectionWithNameAtExactlyConfiguredLimit() {
    // given
    when(collectionWriter.createNewCollectionAndReturnId(anyString(), any()))
        .thenReturn(new IdResponseDto("id"));
    final var request = new PartialCollectionDefinitionRequestDto("a".repeat(CONFIGURED_LIMIT));

    // when
    final var result = collectionService.createNewCollectionAndReturnId(USER_ID, request);

    // then
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldRejectUpdatingCollectionWithNameExceedingConfiguredLimit() {
    // given
    final var request = new PartialCollectionDefinitionRequestDto("a".repeat(CONFIGURED_LIMIT + 1));

    // when
    final var thrown =
        assertThatExceptionOfType(OptimizeValidationException.class)
            .isThrownBy(
                () -> collectionService.updatePartialCollection(USER_ID, COLLECTION_ID, request));

    // then
    thrown.withMessage("Collection names cannot be greater than 256 characters");
    verify(collectionWriter, never()).updateCollection(any(), anyString());
  }

  @Test
  void shouldClampRatherThanRejectGeneratedCopyNameThatOverflowsTheLimit() {
    // given a source name at exactly the limit, so the appended " – Copy" suffix would overflow it
    givenStoredCollection("a".repeat(CONFIGURED_LIMIT));

    // when no name is supplied, so Optimize generates one
    collectionService.copyCollection(USER_ID, COLLECTION_ID, null);

    // then the copy is created with a clamped name rather than rejected
    assertThat(capturedCreatedCollectionName()).hasSize(CONFIGURED_LIMIT);
  }

  @Test
  void shouldAllowCopyingCollectionWhoseStoredNamePredatesTheLimit() {
    // given an existing collection saved before the limit existed
    givenStoredCollection("a".repeat(20_000));

    // when
    collectionService.copyCollection(USER_ID, COLLECTION_ID, null);

    // then the copy still succeeds, clamped to the configured limit
    assertThat(capturedCreatedCollectionName()).hasSize(CONFIGURED_LIMIT);
  }

  @Test
  void shouldRejectUserSuppliedCopyNameExceedingConfiguredLimit() {
    // given
    givenStoredCollection("short name");

    // when
    final var thrown =
        assertThatExceptionOfType(OptimizeValidationException.class)
            .isThrownBy(
                () ->
                    collectionService.copyCollection(
                        USER_ID, COLLECTION_ID, "a".repeat(CONFIGURED_LIMIT + 1)));

    // then
    thrown.withMessage("Collection names cannot be greater than 256 characters");
    verify(collectionWriter, never()).createNewCollection(any());
  }

  @Test
  void shouldNotValidateNameOnRead() {
    // given an already stored collection whose name predates the limit
    when(collectionReader.getCollection(COLLECTION_ID))
        .thenReturn(Optional.of(collectionWithName("a".repeat(20_000))));

    // when
    final var result = collectionService.getCollectionDefinition(COLLECTION_ID);

    // then
    assertThat(result.getName()).hasSize(20_000);
  }

  private String capturedCreatedCollectionName() {
    final var created = ArgumentCaptor.forClass(CollectionDefinitionDto.class);
    verify(collectionWriter).createNewCollection(created.capture());
    return created.getValue().getName();
  }

  private void givenStoredCollection(final String name) {
    final var stored = collectionWithName(name);
    when(authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(
            USER_ID, COLLECTION_ID))
        .thenReturn(new AuthorizedCollectionDefinitionDto(stored));
    when(collectionReader.getCollection(COLLECTION_ID)).thenReturn(Optional.of(stored));
  }

  private CollectionDefinitionDto collectionWithName(final String name) {
    return new CollectionDefinitionDto(
        null, OffsetDateTime.now(), COLLECTION_ID, name, OffsetDateTime.now(), USER_ID, USER_ID);
  }
}
