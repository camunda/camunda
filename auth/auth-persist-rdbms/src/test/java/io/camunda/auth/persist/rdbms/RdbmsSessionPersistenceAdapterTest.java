/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.model.SessionData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RdbmsSessionPersistenceAdapterTest {

  @Mock private WebSessionMapper mapper;
  @InjectMocks private RdbmsSessionPersistenceAdapter adapter;

  @Test
  void shouldFindSessionById() {
    // given
    final var entity = createWebSessionEntity("session-1");
    when(mapper.findById("session-1")).thenReturn(entity);

    // when
    final var result = adapter.findById("session-1");

    // then
    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo("session-1");
    assertThat(result.creationTime()).isEqualTo(entity.getCreationTime());
    assertThat(result.lastAccessedTime()).isEqualTo(entity.getLastAccessedTime());
    assertThat(result.maxInactiveIntervalInSeconds())
        .isEqualTo(entity.getMaxInactiveIntervalInSeconds());
  }

  @Test
  void shouldReturnNullWhenSessionNotFound() {
    // given
    when(mapper.findById("nonexistent")).thenReturn(null);

    // when
    final var result = adapter.findById("nonexistent");

    // then
    assertThat(result).isNull();
  }

  @Test
  void shouldInsertNewSessionWhenUpdateAffectsZeroRows() {
    // given
    final var sessionData = createSessionData("session-1");
    when(mapper.update(any(WebSessionEntity.class))).thenReturn(0);

    // when
    adapter.save(sessionData);

    // then
    verify(mapper).update(any(WebSessionEntity.class));
    verify(mapper).insert(any(WebSessionEntity.class));
  }

  @Test
  void shouldUpdateExistingSessionWithoutInsert() {
    // given
    final var sessionData = createSessionData("session-1");
    when(mapper.update(any(WebSessionEntity.class))).thenReturn(1);

    // when
    adapter.save(sessionData);

    // then
    verify(mapper).update(any(WebSessionEntity.class));
    verify(mapper, never()).insert(any(WebSessionEntity.class));
  }

  @Test
  void shouldDeleteSessionById() {
    // when
    adapter.deleteById("session-1");

    // then
    verify(mapper).deleteById("session-1");
  }

  @Test
  void shouldFindAllSessions() {
    // given
    final var entity1 = createWebSessionEntity("session-1");
    final var entity2 = createWebSessionEntity("session-2");
    when(mapper.findAll()).thenReturn(List.of(entity1, entity2));

    // when
    final var results = adapter.findAll();

    // then
    assertThat(results).hasSize(2);
    assertThat(results).extracting(SessionData::id).containsExactly("session-1", "session-2");
  }

  @Test
  void shouldReturnEmptyListWhenNoSessions() {
    // given
    when(mapper.findAll()).thenReturn(List.of());

    // when
    final var results = adapter.findAll();

    // then
    assertThat(results).isEmpty();
  }

  @Test
  void shouldDeleteExpiredSessions() {
    // when
    adapter.deleteExpired();

    // then
    verify(mapper).deleteExpired(anyLong());
  }

  @Test
  void shouldMapNullAttributesToEmptyMap() {
    // given
    final var entity = createWebSessionEntity("session-1");
    entity.setAttributes(null);
    when(mapper.findById("session-1")).thenReturn(entity);

    // when
    final var result = adapter.findById("session-1");

    // then
    assertThat(result).isNotNull();
    assertThat(result.attributes()).isNotNull().isEmpty();
  }

  private static WebSessionEntity createWebSessionEntity(final String sessionId) {
    final var entity = new WebSessionEntity();
    entity.setSessionId(sessionId);
    entity.setCreationTime(System.currentTimeMillis());
    entity.setLastAccessedTime(System.currentTimeMillis());
    entity.setMaxInactiveIntervalInSeconds(1800);
    entity.setAttributes(Map.of());
    return entity;
  }

  private static SessionData createSessionData(final String id) {
    return new SessionData(
        id, System.currentTimeMillis(), System.currentTimeMillis(), 1800, Map.of());
  }
}
