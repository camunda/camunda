/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.session.WebSessionMapper.SpringBasedWebSessionAttributeConverter;
import io.camunda.search.clients.PersistentSessionSearchClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.session.MapSession;

class WebSessionRepositoryTest {

  private WebSessionRepository webSessionRepository;
  private PersistentSessionSearchClient client;

  @BeforeEach
  void setUp() {
    client = new PersistentSessionSearchClientStub();
    webSessionRepository =
        new WebSessionRepository(
            client,
            new WebSessionMapper(
                new SpringBasedWebSessionAttributeConverter(new GenericConversionService())),
            null);
  }

  @Test
  void createSessionReturnSession() {
    // when
    final var session = webSessionRepository.createSession();

    // then
    assertThat(session).isNotNull();
    assertThat(session.getId()).isNotNull();
    assertThat(session.getLastAccessedTime()).isNotNull();
    assertThat(session.getCreationTime()).isNotNull();
    assertThat(session.getMaxInactiveInterval()).isNotNull();
  }

  @Test
  void saveValidSessionPersistsSession() {
    // given
    final var session = webSessionRepository.createSession();
    session.setLastAccessedTime(Instant.now());

    // when
    webSessionRepository.save(session);

    // then
    assertThat(webSessionRepository.findById(session.getId())).isNotNull();
  }

  @Test
  void saveExpiredSessionDeleteSession() {
    // given
    final var session = webSessionRepository.createSession();
    session.setLastAccessedTime(Instant.now());
    webSessionRepository.save(session);

    // when
    session.setLastAccessedTime(
        Instant.now().minusSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL.toSeconds() * 2));
    webSessionRepository.save(session);

    // then
    assertThat(webSessionRepository.findById(session.getId())).isNull();
  }

  @Test
  void findByNotExistingIdReturnsNull() {
    assertThat(webSessionRepository.findById("not-existing-id")).isNull();
  }

  @Test
  void deleteById() {
    // given
    final var session = webSessionRepository.createSession();
    session.setLastAccessedTime(Instant.now());
    webSessionRepository.save(session);

    // when
    webSessionRepository.deleteById(session.getId());

    // then
    assertThat(webSessionRepository.findById(session.getId())).isNull();
  }

  @Test
  void deleteExpiredWebSessions() {
    // given
    final var expiredLastAccessedTime =
        Instant.now().minusSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL.toSeconds() * 2);
    client.upsertPersistentWebSession(
        new PersistentWebSessionEntity(
            "s1",
            expiredLastAccessedTime.toEpochMilli(),
            expiredLastAccessedTime.toEpochMilli(),
            MapSession.DEFAULT_MAX_INACTIVE_INTERVAL.toSeconds(),
            Map.of()));
    client.upsertPersistentWebSession(
        new PersistentWebSessionEntity(
            "s2",
            expiredLastAccessedTime.toEpochMilli(),
            expiredLastAccessedTime.toEpochMilli(),
            MapSession.DEFAULT_MAX_INACTIVE_INTERVAL.toSeconds(),
            Map.of()));
    client.upsertPersistentWebSession(
        new PersistentWebSessionEntity(
            "s3",
            expiredLastAccessedTime.toEpochMilli(),
            expiredLastAccessedTime.toEpochMilli(),
            MapSession.DEFAULT_MAX_INACTIVE_INTERVAL.toSeconds(),
            Map.of()));

    assertThat(client.getAllPersistentWebSessions()).hasSize(3);

    // when
    webSessionRepository.deleteExpiredWebSessions();

    // then
    assertThat(client.getAllPersistentWebSessions()).isEmpty();
  }

  static final class PersistentSessionSearchClientStub implements PersistentSessionSearchClient {

    private final Map<String, PersistentWebSessionEntity> sessions;

    private PersistentSessionSearchClientStub() {
      sessions = new HashMap<>();
    }

    @Override
    public PersistentWebSessionEntity getPersistentWebSession(final String sessionId) {
      return sessions.get(sessionId);
    }

    @Override
    public void upsertPersistentWebSession(final PersistentWebSessionEntity webSession) {
      sessions.put(webSession.id(), webSession);
    }

    @Override
    public void deletePersistentWebSession(final String sessionId) {
      sessions.remove(sessionId);
    }

    @Override
    public List<PersistentWebSessionEntity> getAllPersistentWebSessions() {
      return new ArrayList<>(sessions.values());
    }
  }
}
