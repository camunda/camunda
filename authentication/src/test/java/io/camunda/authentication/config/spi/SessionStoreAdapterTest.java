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
import io.camunda.security.api.model.session.PersistentSession;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SessionStoreAdapter}. */
class SessionStoreAdapterTest {

  @Test
  void shouldOperateOnlyOnItsOwnClient() {
    // given — the adapter is bound to one client, with a second client left untouched
    final var own = new PersistentWebSessionClientStub();
    final var other = new PersistentWebSessionClientStub();
    final var adapter = new SessionStoreAdapter(own);

    // when
    adapter.upsert(new PersistentSession("s1", 100L, 200L, 1800L, Map.of("a", new byte[] {1})));

    // then — written to its own client only
    assertThat(own.getPersistentWebSession("s1")).isNotNull();
    assertThat(other.getPersistentWebSession("s1")).isNull();
    // and readable back through the adapter
    assertThat(adapter.get("s1")).isNotNull();
    assertThat(adapter.get("s1").id()).isEqualTo("s1");
    // delete removes it
    adapter.delete("s1");
    assertThat(adapter.get("s1")).isNull();
  }

  @Test
  void shouldReturnOnlyItsOwnSessionsFromGetAll() {
    // given
    final var client = new PersistentWebSessionClientStub();
    final var adapter = new SessionStoreAdapter(client);
    client.upsertPersistentWebSession(
        new PersistentWebSessionEntity("s1", 1L, 2L, 1800L, Map.of()));
    client.upsertPersistentWebSession(
        new PersistentWebSessionEntity("s2", 1L, 2L, 1800L, Map.of()));

    // when / then
    assertThat(adapter.getAll())
        .extracting(PersistentSession::id)
        .containsExactlyInAnyOrder("s1", "s2");
  }

  @Test
  void shouldRetryTransientUpsertFailureThenSwallow() {
    // given — a client that always fails transiently
    final var attempts = new AtomicInteger();
    final PersistentWebSessionClient failing =
        new PersistentWebSessionClientStub() {
          @Override
          public void upsertPersistentWebSession(final PersistentWebSessionEntity entity) {
            attempts.incrementAndGet();
            throw new RuntimeException("Connection refused");
          }
        };
    final var adapter = new SessionStoreAdapter(failing);

    // when / then — retried three times, and the failure never propagates to the save path
    assertThatNoException()
        .isThrownBy(() -> adapter.upsert(new PersistentSession("s1", 1L, 2L, 1800L, Map.of())));
    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  void shouldNotRetryNonTransientCamundaSearchException() {
    // given — a client whose failure reason is not one of the transient reasons
    final var attempts = new AtomicInteger();
    final PersistentWebSessionClient failing =
        new PersistentWebSessionClientStub() {
          @Override
          public void upsertPersistentWebSession(final PersistentWebSessionEntity entity) {
            attempts.incrementAndGet();
            throw new CamundaSearchException("invalid session", Reason.INVALID_ARGUMENT);
          }
        };
    final var adapter = new SessionStoreAdapter(failing);

    // when / then — not retried, and the failure never propagates to the save path
    assertThatNoException()
        .isThrownBy(() -> adapter.upsert(new PersistentSession("s1", 1L, 2L, 1800L, Map.of())));
    assertThat(attempts.get()).isEqualTo(1);
  }

  @Test
  void shouldRecoverAfterTransientCamundaSearchException() {
    // given — a client that fails transiently once, then succeeds
    final var attempts = new AtomicInteger();
    final PersistentWebSessionClient recovering =
        new PersistentWebSessionClientStub() {
          @Override
          public void upsertPersistentWebSession(final PersistentWebSessionEntity entity) {
            if (attempts.incrementAndGet() == 1) {
              throw new CamundaSearchException("connection refused", Reason.CONNECTION_FAILED);
            }
            super.upsertPersistentWebSession(entity);
          }
        };
    final var adapter = new SessionStoreAdapter(recovering);

    // when
    adapter.upsert(new PersistentSession("s1", 1L, 2L, 1800L, Map.of()));

    // then — retried once and the second attempt succeeded, so the session is stored
    assertThat(attempts.get()).isEqualTo(2);
    assertThat(adapter.get("s1")).isNotNull();
  }

  @Test
  void shouldReturnNullOnGetWhenAbsent() {
    assertThat(new SessionStoreAdapter(new PersistentWebSessionClientStub()).get("x")).isNull();
  }
}
