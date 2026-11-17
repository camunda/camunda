/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.WebSessionDto;
import io.camunda.optimize.service.db.repository.PersistentWebSessionRepository;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.security.api.model.session.PersistentSession;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CSL owns the session lifecycle and only ever sees a {@link PersistentSession}, so the adapter's
 * whole job is a faithful translation to and from the stored document. A field dropped or swapped
 * here surfaces as a session that silently expires early, or one that never expires.
 */
@ExtendWith(MockitoExtension.class)
class OptimizeSessionStoreAdapterTest {

  private static final String SESSION_ID = "session-id";
  private static final String ATTRIBUTE_NAME = "SPRING_SECURITY_CONTEXT";
  private static final byte[] ATTRIBUTE_VALUE = "principal".getBytes(UTF_8);
  private static final long CREATION_TIME = 1000L;
  private static final long LAST_ACCESSED_TIME = 2000L;
  private static final long MAX_INACTIVE_INTERVAL_SECONDS = 1800L;

  @Mock private PersistentWebSessionRepository repository;

  private OptimizeSessionStoreAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new OptimizeSessionStoreAdapter(repository);
  }

  @Test
  void shouldReturnNullWhenSessionIsAbsent() {
    // given
    when(repository.get(SESSION_ID)).thenReturn(Optional.empty());

    // when
    final PersistentSession session = adapter.get(SESSION_ID);

    // then the port contract is a null, which tells CSL to start a fresh session
    assertThat(session).isNull();
  }

  @Test
  void shouldReadBackEveryStoredField() {
    // given
    when(repository.get(SESSION_ID)).thenReturn(Optional.of(webSessionDto()));

    // when
    final PersistentSession session = adapter.get(SESSION_ID);

    // then
    assertThat(session.id()).isEqualTo(SESSION_ID);
    assertThat(session.creationTime()).isEqualTo(CREATION_TIME);
    assertThat(session.lastAccessedTime()).isEqualTo(LAST_ACCESSED_TIME);
    assertThat(session.maxInactiveIntervalInSeconds()).isEqualTo(MAX_INACTIVE_INTERVAL_SECONDS);
    assertThat(session.attributes()).containsExactly(Map.entry(ATTRIBUTE_NAME, ATTRIBUTE_VALUE));
  }

  @Test
  void shouldTreatMissingAttributesAsEmpty() {
    // given a document written without any attribute
    final WebSessionDto dto = webSessionDto();
    dto.setAttributes(null);
    when(repository.get(SESSION_ID)).thenReturn(Optional.of(dto));

    // when
    final PersistentSession session = adapter.get(SESSION_ID);

    // then PersistentSession rejects a null attribute map, so the adapter substitutes an empty one
    assertThat(session.attributes()).isEmpty();
  }

  @Test
  void shouldStoreEveryFieldOfASession() {
    // when
    adapter.upsert(persistentSession());

    // then
    final ArgumentCaptor<WebSessionDto> stored = ArgumentCaptor.forClass(WebSessionDto.class);
    verify(repository).upsert(stored.capture());
    final WebSessionDto dto = stored.getValue();
    assertThat(dto.getId()).isEqualTo(SESSION_ID);
    assertThat(dto.getCreationTime()).isEqualTo(CREATION_TIME);
    assertThat(dto.getLastAccessedTime()).isEqualTo(LAST_ACCESSED_TIME);
    assertThat(dto.getMaxInactiveIntervalInSeconds()).isEqualTo(MAX_INACTIVE_INTERVAL_SECONDS);
    assertThat(dto.getAttributes()).containsExactly(Map.entry(ATTRIBUTE_NAME, ATTRIBUTE_VALUE));
  }

  @Test
  void shouldNotFailTheRequestWhenTheSessionCannotBeStored() {
    // given a database that is temporarily unreachable
    doThrow(new OptimizeRuntimeException("elasticsearch is down"))
        .when(repository)
        .upsert(any(WebSessionDto.class));

    // when
    // then Spring Session saves while the response is committing, so the blip must not surface
    assertThatNoException().isThrownBy(() -> adapter.upsert(persistentSession()));
  }

  @Test
  void shouldPropagateFailuresWhenTheSessionCannotBeLoaded() {
    // given
    when(repository.get(SESSION_ID))
        .thenThrow(new OptimizeRuntimeException("elasticsearch is down"));

    // when
    // then an unreadable session must not look like an absent one, which would silently log the
    // user out and start a fresh session
    assertThatThrownBy(() -> adapter.get(SESSION_ID)).isInstanceOf(OptimizeRuntimeException.class);
  }

  @Test
  void shouldDeleteBySessionId() {
    // when
    adapter.delete(SESSION_ID);

    // then
    verify(repository).delete(SESSION_ID);
  }

  @Test
  void shouldMapEverySessionOfTheExpirySweep() {
    // given
    final WebSessionDto other = webSessionDto();
    other.setId("other-session-id");
    when(repository.getAll()).thenReturn(List.of(webSessionDto(), other));

    // when
    final List<PersistentSession> sessions = adapter.getAll();

    // then
    assertThat(sessions)
        .extracting(PersistentSession::id)
        .containsExactly(SESSION_ID, "other-session-id");
  }

  private static WebSessionDto webSessionDto() {
    return new WebSessionDto(
        SESSION_ID,
        CREATION_TIME,
        LAST_ACCESSED_TIME,
        MAX_INACTIVE_INTERVAL_SECONDS,
        Map.of(ATTRIBUTE_NAME, ATTRIBUTE_VALUE));
  }

  private static PersistentSession persistentSession() {
    return new PersistentSession(
        SESSION_ID,
        CREATION_TIME,
        LAST_ACCESSED_TIME,
        MAX_INACTIVE_INTERVAL_SECONDS,
        Map.of(ATTRIBUTE_NAME, ATTRIBUTE_VALUE));
  }
}
