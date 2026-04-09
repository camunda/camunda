/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.authentication.session.WebSessionMapper.SpringBasedWebSessionAttributeConverter;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.search.query.SearchQueryResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

  static Stream<Arguments> upsertExceptionProvider() {
    return Stream.of(
        Arguments.of(
            "CamundaSearchException",
            (Supplier<RuntimeException>)
                () -> new CamundaSearchException("Failed to execute index request")),
        Arguments.of(
            "RuntimeException",
            (Supplier<RuntimeException>) () -> new RuntimeException("Connection refused")));
  }

  @ParameterizedTest(name = "should not propagate {0} when upsert fails after retries")
  @MethodSource("upsertExceptionProvider")
  void shouldNotPropagateExceptionWhenUpsertFailsAfterRetries(
      final String exceptionName, final Supplier<RuntimeException> exceptionSupplier) {
    // given
    final var upsertAttempts = new AtomicInteger(0);
    final PersistentWebSessionClient failingClient =
        new PersistentWebSessionClient() {
          @Override
          public PersistentWebSessionEntity getPersistentWebSession(final String sessionId) {
            return null;
          }

          @Override
          public void upsertPersistentWebSession(
              final PersistentWebSessionEntity persistentWebSessionEntity) {
            upsertAttempts.incrementAndGet();
            throw exceptionSupplier.get();
          }

          @Override
          public void deletePersistentWebSession(final String sessionId) {}

          @Override
          public SearchQueryResult<PersistentWebSessionEntity> getAllPersistentWebSessions() {
            return SearchQueryResult.of(b -> b.items(new ArrayList<>()));
          }
        };
    final var repository =
        new WebSessionRepository(
            failingClient,
            new WebSessionMapper(
                new SpringBasedWebSessionAttributeConverter(new GenericConversionService())),
            null);
    final var webSession = repository.createSession();
    webSession.setLastAccessedTime(Instant.now());

    // when / then
    assertThatNoException().isThrownBy(() -> repository.save(webSession));
    assertThat(upsertAttempts.get()).isEqualTo(3);
  }

  @Test
  void shouldNotRetryNonTransientCamundaSearchException() {
    // given
    final var upsertAttempts = new AtomicInteger(0);
    final PersistentWebSessionClient failingClient =
        new PersistentWebSessionClient() {
          @Override
          public PersistentWebSessionEntity getPersistentWebSession(final String sessionId) {
            return null;
          }

          @Override
          public void upsertPersistentWebSession(
              final PersistentWebSessionEntity persistentWebSessionEntity) {
            upsertAttempts.incrementAndGet();
            throw new CamundaSearchException("Invalid argument", Reason.INVALID_ARGUMENT);
          }

          @Override
          public void deletePersistentWebSession(final String sessionId) {}

          @Override
          public SearchQueryResult<PersistentWebSessionEntity> getAllPersistentWebSessions() {
            return SearchQueryResult.of(b -> b.items(new ArrayList<>()));
          }
        };
    final var repository =
        new WebSessionRepository(
            failingClient,
            new WebSessionMapper(
                new SpringBasedWebSessionAttributeConverter(new GenericConversionService())),
            null);
    final var webSession = repository.createSession();
    webSession.setLastAccessedTime(Instant.now());

    // when / then
    assertThatNoException().isThrownBy(() -> repository.save(webSession));
    assertThat(upsertAttempts.get()).isEqualTo(1);
  }

  @Test
  void shouldSucceedOnRetryAfterTransientFailure() {
    // given
    final var upsertAttempts = new AtomicInteger(0);
    final var savedEntities = new HashMap<String, PersistentWebSessionEntity>();
    final PersistentWebSessionClient transientFailureClient =
        new PersistentWebSessionClient() {
          @Override
          public PersistentWebSessionEntity getPersistentWebSession(final String sessionId) {
            return savedEntities.get(sessionId);
          }

          @Override
          public void upsertPersistentWebSession(
              final PersistentWebSessionEntity persistentWebSessionEntity) {
            if (upsertAttempts.incrementAndGet() < 3) {
              throw new RuntimeException("Connection refused");
            }
            savedEntities.put(persistentWebSessionEntity.id(), persistentWebSessionEntity);
          }

          @Override
          public void deletePersistentWebSession(final String sessionId) {}

          @Override
          public SearchQueryResult<PersistentWebSessionEntity> getAllPersistentWebSessions() {
            return SearchQueryResult.of(b -> b.items(new ArrayList<>(savedEntities.values())));
          }
        };
    final var repository =
        new WebSessionRepository(
            transientFailureClient,
            new WebSessionMapper(
                new SpringBasedWebSessionAttributeConverter(new GenericConversionService())),
            null);
    final var webSession = repository.createSession();
    webSession.setLastAccessedTime(Instant.now());

    // when
    repository.save(webSession);

    // then
    assertThat(upsertAttempts.get()).isEqualTo(3);
    assertThat(transientFailureClient.getPersistentWebSession(webSession.getId())).isNotNull();
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
