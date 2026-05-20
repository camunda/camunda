/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizationType;
import io.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.service.db.reader.CollectionReader;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizedCollectionServiceTest {

  @Mock private CollectionReader collectionReader;
  @Mock private AbstractIdentityService identityService;

  private AuthorizedCollectionService underTest;

  @BeforeEach
  void setUp() {
    underTest = new AuthorizedCollectionService(collectionReader, identityService);
    when(identityService.getAllGroupsOfUser("test-user")).thenReturn(List.of());
  }

  @Test
  void shouldGrantEditorRoleForAutomaticallyCreatedCollectionWhenEntityEditorIsEnabled() {
    // given
    when(collectionReader.getCollection("agentic-control-plane"))
        .thenReturn(Optional.of(newCollection("agentic-control-plane", true)));
    when(identityService.getEnabledAuthorizations())
        .thenReturn(List.of(AuthorizationType.ENTITY_EDITOR));

    // when
    final RoleType currentUserRole =
        underTest
            .getAuthorizedCollectionDefinitionOrFail("test-user", "agentic-control-plane")
            .getCurrentUserRole();

    // then
    assertThat(currentUserRole).isEqualTo(RoleType.EDITOR);
  }

  @Test
  void shouldGrantViewerRoleForAutomaticallyCreatedCollectionWhenEntityEditorIsDisabled() {
    // given
    when(collectionReader.getCollection("agentic-control-plane"))
        .thenReturn(Optional.of(newCollection("agentic-control-plane", true)));
    when(identityService.getEnabledAuthorizations()).thenReturn(List.of());

    // when
    final RoleType currentUserRole =
        underTest
            .getAuthorizedCollectionDefinitionOrFail("test-user", "agentic-control-plane")
            .getCurrentUserRole();

    // then
    assertThat(currentUserRole).isEqualTo(RoleType.VIEWER);
  }

  @Test
  void shouldRequireExplicitRoleForNonSystemCollection() {
    // given
    when(collectionReader.getCollection("custom-collection"))
        .thenReturn(Optional.of(newCollection("custom-collection", false)));

    // when/then
    assertThatExceptionOfType(ForbiddenException.class)
        .isThrownBy(
            () ->
                underTest.getAuthorizedCollectionDefinitionOrFail(
                    "test-user", "custom-collection"));
  }

  @Test
  void shouldIncludeAutomaticallyCreatedCollectionInAuthorizedCollectionListings() {
    // given
    when(collectionReader.getAllCollections())
        .thenReturn(
            List.of(
                newCollection("agentic-control-plane", true),
                newCollection("custom-collection", false)));
    when(identityService.getEnabledAuthorizations()).thenReturn(List.of());

    // when
    final List<AuthorizedCollectionDefinitionDto> authorizedCollections =
        underTest.getAuthorizedCollectionDefinitions("test-user");

    // then
    assertThat(authorizedCollections).hasSize(1);
    assertThat(authorizedCollections.getFirst().getDefinitionDto().getId())
        .isEqualTo("agentic-control-plane");
    assertThat(authorizedCollections.getFirst().getCurrentUserRole()).isEqualTo(RoleType.VIEWER);
  }

  private CollectionDefinitionDto newCollection(
      final String collectionId, final boolean automaticallyCreated) {
    final CollectionDefinitionDto collectionDefinition = new CollectionDefinitionDto();
    collectionDefinition.setId(collectionId);
    collectionDefinition.setData(new CollectionDataDto());
    collectionDefinition.setAutomaticallyCreated(automaticallyCreated);
    return collectionDefinition;
  }
}
