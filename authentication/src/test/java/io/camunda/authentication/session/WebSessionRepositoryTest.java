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
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import io.camunda.search.query.SearchQueryResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.session.MapSession;

class WebSessionRepositoryTest {

  private WebSessionRepository webSessionRepository;
  private PersistentWebSessionClient persistentWebSessionClient;

  @BeforeEach
  void setUp() {
    persistentWebSessionClient = new PersistentWebSessionClientStub();
    webSessionRepository =
        new WebSessionRepository(
            persistentWebSessionClient,
            new WebSessionMapper(
                new SpringBasedWebSessionAttributeConverter(new GenericConversionService())),
            null);
  }

  @Test
  void createSessionReturnSession() {
    // when
    final var webSession = webSessionRepository.createSession();

    // then
    assertThat(webSession).isNotNull();
    assertThat(webSession.getId()).isNotNull();
    assertThat(webSession.getLastAccessedTime()).isNotNull();
    assertThat(webSession.getCreationTime()).isNotNull();
    assertThat(webSession.getMaxInactiveInterval()).isNotNull();
  }

  @Test
  void saveValidSessionPersistsSession() {
    // given
    final var webSession = webSessionRepository.createSession();
    webSession.setLastAccessedTime(Instant.now());

    // when
    webSessionRepository.save(webSession);

    // then
    assertThat(webSessionRepository.findById(webSession.getId())).isNotNull();
  }

  @Test
  void saveExpiredSessionDeleteSession() {
    // given
    final var webSession = webSessionRepository.createSession();
    webSession.setLastAccessedTime(Instant.now());
    webSessionRepository.save(webSession);

    // when
    webSession.setLastAccessedTime(
        Instant.now().minusSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL.toSeconds() * 2));
    webSessionRepository.save(webSession);

    // then
    assertThat(webSessionRepository.findById(webSession.getId())).isNull();
  }

  @Test
  void findByNotExistingIdReturnsNull() {
    assertThat(webSessionRepository.findById("not-existing-id")).isNull();
  }

  @Test
  void deleteById() {
    // given
    final var webSession = webSessionRepository.createSession();
    webSession.setLastAccessedTime(Instant.now());
    webSessionRepository.save(webSession);

    // when
    webSessionRepository.deleteById(webSession.getId());

    // then
    assertThat(webSessionRepository.findById(webSession.getId())).isNull();
  }

  @Test
  void saveSessionWithLockAndRefreshAttributePersistsSession() {
    // given
    final var webSession = webSessionRepository.createSession();
    webSession.setLastAccessedTime(Instant.now());
    webSession.setAttribute("lock", webSession.getId() + "LOCK");
    webSession.setAttribute("refresh", Instant.now());

    // when
    webSessionRepository.save(webSession);

    // then
    assertThat(webSessionRepository.findById(webSession.getId())).isNotNull();
  }

  @Test
  void deleteExpiredWebSessions() {
    // given
    final var expiredLastAccessedTime =
        Instant.now().minusSeconds(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL.toSeconds() * 2);
    persistentWebSessionClient.upsertPersistentWebSession(
        new PersistentWebSessionEntity(
            "s1",
            expiredLastAccessedTime.toEpochMilli(),
            expiredLastAccessedTime.toEpochMilli(),
            MapSession.DEFAULT_MAX_INACTIVE_INTERVAL.toSeconds(),
            Map.of()));
    persistentWebSessionClient.upsertPersistentWebSession(
        new PersistentWebSessionEntity(
            "s2",
            expiredLastAccessedTime.toEpochMilli(),
            expiredLastAccessedTime.toEpochMilli(),
            MapSession.DEFAULT_MAX_INACTIVE_INTERVAL.toSeconds(),
            Map.of()));
    persistentWebSessionClient.upsertPersistentWebSession(
        new PersistentWebSessionEntity(
            "s3",
            expiredLastAccessedTime.toEpochMilli(),
            expiredLastAccessedTime.toEpochMilli(),
            MapSession.DEFAULT_MAX_INACTIVE_INTERVAL.toSeconds(),
            Map.of()));

    assertThat(persistentWebSessionClient.getAllPersistentWebSessions().items()).hasSize(3);

    // when
    webSessionRepository.deleteExpiredWebSessions();

    // then
    assertThat(persistentWebSessionClient.getAllPersistentWebSessions().items()).isEmpty();
  }

  static final class PersistentWebSessionClientStub implements PersistentWebSessionClient {

    private final Map<String, PersistentWebSessionEntity> persistentWebSessions;

    private PersistentWebSessionClientStub() {
      persistentWebSessions = new HashMap<>();
    }

    @Override
    public PersistentWebSessionEntity getPersistentWebSession(final String sessionId) {
      return persistentWebSessions.get(sessionId);
    }

    @Override
    public void upsertPersistentWebSession(
        final PersistentWebSessionEntity persistentWebSessionEntity) {
      persistentWebSessions.put(persistentWebSessionEntity.id(), persistentWebSessionEntity);
    }

    @Override
    public void deletePersistentWebSession(final String sessionId) {
      persistentWebSessions.remove(sessionId);
    }

    @Override
    public SearchQueryResult<PersistentWebSessionEntity> getAllPersistentWebSessions() {
      return SearchQueryResult.of(b -> b.items(new ArrayList<>(persistentWebSessions.values())));
    }
  }
}
