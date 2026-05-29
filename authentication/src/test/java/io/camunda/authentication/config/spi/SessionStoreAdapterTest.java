/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.session.PersistentSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SessionStoreAdapterTest {

  @Test
  void shouldMapAndUpsertSession() {
    // given
    final var client = new PersistentWebSessionClientStub();
    final var adapter = new SessionStoreAdapter(client);
    final var attributes = Map.of("a", new byte[] {1, 2, 3});
    final var session = new PersistentSession("s1", 100L, 200L, 1800L, attributes);

    // when
    adapter.upsert(session);

    // then
    final var stored = client.getPersistentWebSession("s1");
    assertThat(stored).isNotNull();
    assertThat(stored.id()).isEqualTo("s1");
    assertThat(stored.creationTime()).isEqualTo(100L);
    assertThat(stored.lastAccessedTime()).isEqualTo(200L);
    assertThat(stored.maxInactiveIntervalInSeconds()).isEqualTo(1800L);
    assertThat(stored.attributes()).isEqualTo(attributes);
  }

  @Test
  void shouldMapStoredEntityToPersistentSessionOnGet() {
    // given
    final var client = new PersistentWebSessionClientStub();
    final var adapter = new SessionStoreAdapter(client);
    final var attributes = Map.of("a", new byte[] {4, 5});
    client.upsertPersistentWebSession(
        new PersistentWebSessionEntity("s1", 100L, 200L, 1800L, attributes));

    // when
    final var session = adapter.get("s1");

    // then
    assertThat(session).isNotNull();
    assertThat(session.id()).isEqualTo("s1");
    assertThat(session.creationTime()).isEqualTo(100L);
    assertThat(session.lastAccessedTime()).isEqualTo(200L);
    assertThat(session.maxInactiveIntervalInSeconds()).isEqualTo(1800L);
    assertThat(session.attributes()).isEqualTo(attributes);
  }

  @Test
  void shouldReturnNullOnGetWhenAbsent() {
    // given
    final var adapter = new SessionStoreAdapter(new PersistentWebSessionClientStub());

    // when / then
    assertThat(adapter.get("missing")).isNull();
  }

  @Test
  void shouldDeleteSession() {
    // given
    final var client = new PersistentWebSessionClientStub();
    final var adapter = new SessionStoreAdapter(client);
    client.upsertPersistentWebSession(
        new PersistentWebSessionEntity("s1", 100L, 200L, 1800L, Map.of()));

    // when
    adapter.delete("s1");

    // then
    assertThat(client.getPersistentWebSession("s1")).isNull();
  }

  @Test
  void shouldReturnAllSessionsAsPersistentSessions() {
    // given
    final var client = new PersistentWebSessionClientStub();
    final var adapter = new SessionStoreAdapter(client);
    client.upsertPersistentWebSession(
        new PersistentWebSessionEntity("s1", 100L, 200L, 1800L, Map.of()));
    client.upsertPersistentWebSession(
        new PersistentWebSessionEntity("s2", 100L, 200L, 1800L, Map.of()));

    // when
    final var sessions = adapter.getAll();

    // then
    assertThat(sessions).hasSize(2);
    assertThat(sessions).extracting(PersistentSession::id).containsExactlyInAnyOrder("s1", "s2");
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
        new PersistentWebSessionClientStub() {
          @Override
          public void upsertPersistentWebSession(final PersistentWebSessionEntity entity) {
            upsertAttempts.incrementAndGet();
            throw exceptionSupplier.get();
          }
        };
    final var adapter = new SessionStoreAdapter(failingClient);

    // when / then
    assertThatNoException()
        .isThrownBy(() -> adapter.upsert(new PersistentSession("s1", 100L, 200L, 1800L, Map.of())));
    assertThat(upsertAttempts.get()).isEqualTo(3);
  }

  @Test
  void shouldNotRetryNonTransientCamundaSearchException() {
    // given
    final var upsertAttempts = new AtomicInteger(0);
    final PersistentWebSessionClient failingClient =
        new PersistentWebSessionClientStub() {
          @Override
          public void upsertPersistentWebSession(final PersistentWebSessionEntity entity) {
            upsertAttempts.incrementAndGet();
            throw new CamundaSearchException("Invalid argument", Reason.INVALID_ARGUMENT);
          }
        };
    final var adapter = new SessionStoreAdapter(failingClient);

    // when / then
    assertThatNoException()
        .isThrownBy(() -> adapter.upsert(new PersistentSession("s1", 100L, 200L, 1800L, Map.of())));
    assertThat(upsertAttempts.get()).isEqualTo(1);
  }

  @Test
  void shouldSucceedOnRetryAfterTransientFailure() {
    // given
    final var upsertAttempts = new AtomicInteger(0);
    final var client =
        new PersistentWebSessionClientStub() {
          @Override
          public void upsertPersistentWebSession(final PersistentWebSessionEntity entity) {
            if (upsertAttempts.incrementAndGet() < 3) {
              throw new RuntimeException("Connection refused");
            }
            super.upsertPersistentWebSession(entity);
          }
        };
    final var adapter = new SessionStoreAdapter(client);

    // when
    adapter.upsert(new PersistentSession("s1", 100L, 200L, 1800L, Map.of()));

    // then
    assertThat(upsertAttempts.get()).isEqualTo(3);
    assertThat(adapter.get("s1")).isNotNull();
  }

  static class PersistentWebSessionClientStub implements PersistentWebSessionClient {

    private final Map<String, PersistentWebSessionEntity> sessions = new HashMap<>();

    @Override
    public PersistentWebSessionEntity getPersistentWebSession(final String sessionId) {
      return sessions.get(sessionId);
    }

    @Override
    public void upsertPersistentWebSession(final PersistentWebSessionEntity entity) {
      sessions.put(entity.id(), entity);
    }

    @Override
    public void deletePersistentWebSession(final String sessionId) {
      sessions.remove(sessionId);
    }

    @Override
    public SearchQueryResult<PersistentWebSessionEntity> getAllPersistentWebSessions() {
      return SearchQueryResult.of(b -> b.items(new ArrayList<>(sessions.values())));
    }
  }
}
