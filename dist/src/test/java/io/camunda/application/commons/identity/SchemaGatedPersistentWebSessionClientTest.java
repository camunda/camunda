/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import io.camunda.search.query.SearchQueryResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaGatedPersistentWebSessionClientTest {

  private final PersistentWebSessionClient delegate = mock(PersistentWebSessionClient.class);

  @Test
  void shouldSkipUpsertWhenSchemaNotInitialized() {
    // given
    final var client = new SchemaGatedPersistentWebSessionClient(delegate, "pt-1", () -> false);
    final var session = session("session-1");

    // when
    client.upsertPersistentWebSession(session);

    // then
    verify(delegate, never()).upsertPersistentWebSession(session);
  }

  @Test
  void shouldDelegateUpsertWhenSchemaInitialized() {
    // given
    final var client = new SchemaGatedPersistentWebSessionClient(delegate, "pt-1", () -> true);
    final var session = session("session-1");

    // when
    client.upsertPersistentWebSession(session);

    // then
    verify(delegate).upsertPersistentWebSession(session);
  }

  @Test
  void shouldAlwaysDelegateGet() {
    // given
    final var client = new SchemaGatedPersistentWebSessionClient(delegate, "pt-1", () -> false);
    final var session = session("session-1");
    when(delegate.getPersistentWebSession("session-1")).thenReturn(session);

    // when
    final var result = client.getPersistentWebSession("session-1");

    // then
    assertThat(result).isEqualTo(session);
  }

  @Test
  void shouldAlwaysDelegateDelete() {
    // given
    final var client = new SchemaGatedPersistentWebSessionClient(delegate, "pt-1", () -> false);

    // when
    client.deletePersistentWebSession("session-1");

    // then
    verify(delegate).deletePersistentWebSession("session-1");
  }

  @Test
  void shouldAlwaysDelegateGetAll() {
    // given
    final var client = new SchemaGatedPersistentWebSessionClient(delegate, "pt-1", () -> false);
    final SearchQueryResult<PersistentWebSessionEntity> expected =
        SearchQueryResult.of(b -> b.items(List.of(session("session-1"))));
    when(delegate.getAllPersistentWebSessions()).thenReturn(expected);

    // when
    final var result = client.getAllPersistentWebSessions();

    // then
    assertThat(result).isEqualTo(expected);
  }

  private static PersistentWebSessionEntity session(final String id) {
    return new PersistentWebSessionEntity(id, 1L, 1L, 1800L, Map.of());
  }
}
