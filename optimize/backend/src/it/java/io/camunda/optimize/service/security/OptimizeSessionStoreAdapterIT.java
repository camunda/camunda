/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.rest.security.csl.OptimizeSessionStoreAdapter;
import io.camunda.optimize.service.db.repository.PersistentWebSessionRepository;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.security.api.model.session.PersistentSession;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Round-trips CSL's server-side sessions through the real {@code web-session} index (ADR-0038).
 * Covers what a mocked repository cannot: that the index the schema manager creates actually
 * accepts the document, and that the Base64-encoded attribute map survives the trip.
 *
 * <p>The adapter is constructed directly rather than taken from the context: its bean only exists
 * with {@code optimize.security.csl.enabled=true}, which would swap the whole security chain and
 * break this rig's authentication. The store path exercised here is the same either way.
 *
 * <p>Runs against both Elasticsearch and OpenSearch: the rig injects the {@link
 * PersistentWebSessionRepository} implementation for the database under test, so the same
 * assertions pin the behaviour of both stores.
 */
public class OptimizeSessionStoreAdapterIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final String ATTRIBUTE_NAME = "SPRING_SECURITY_CONTEXT";
  private static final byte[] ATTRIBUTE_VALUE = "serialized-principal".getBytes(UTF_8);
  private static final long MAX_INACTIVE_INTERVAL_SECONDS = 1800L;

  private OptimizeSessionStoreAdapter sessionStore;

  @BeforeEach
  void setUp() {
    sessionStore =
        new OptimizeSessionStoreAdapter(
            embeddedOptimizeExtension.getBean(PersistentWebSessionRepository.class));
  }

  @Test
  void shouldStoreAndLoadASession() {
    // given
    final PersistentSession session = newSession();

    // when
    sessionStore.upsert(session);

    // then the session is readable without an index refresh, as it must be for the very next
    // request
    final PersistentSession loaded = sessionStore.get(session.id());
    assertThat(loaded).isNotNull();
    assertThat(loaded.id()).isEqualTo(session.id());
    assertThat(loaded.creationTime()).isEqualTo(session.creationTime());
    assertThat(loaded.lastAccessedTime()).isEqualTo(session.lastAccessedTime());
    assertThat(loaded.maxInactiveIntervalInSeconds()).isEqualTo(MAX_INACTIVE_INTERVAL_SECONDS);
    assertThat(loaded.attributes().get(ATTRIBUTE_NAME)).isEqualTo(ATTRIBUTE_VALUE);
  }

  @Test
  void shouldReturnNullForAnUnknownSession() {
    // when
    final PersistentSession loaded = sessionStore.get(IdGenerator.getNextId());

    // then
    assertThat(loaded).isNull();
  }

  @Test
  void shouldRefreshTheSessionOnEveryAccess() {
    // given a stored session
    final PersistentSession session = newSession();
    sessionStore.upsert(session);

    // when the same session is written again with a later access time, as CSL does per request
    final long laterAccess = session.lastAccessedTime() + 60_000L;
    sessionStore.upsert(
        new PersistentSession(
            session.id(),
            session.creationTime(),
            laterAccess,
            MAX_INACTIVE_INTERVAL_SECONDS,
            session.attributes()));

    // then the update replaced the document instead of adding a second one
    assertThat(sessionStore.get(session.id()).lastAccessedTime()).isEqualTo(laterAccess);
    assertThat(sessionsInStore()).extracting(PersistentSession::id).containsOnlyOnce(session.id());
  }

  @Test
  void shouldDeleteASession() {
    // given a stored session
    final PersistentSession session = newSession();
    sessionStore.upsert(session);

    // when the user logs out
    sessionStore.delete(session.id());

    // then the session document is gone, so the session cannot be resumed anywhere in the cluster
    assertThat(sessionStore.get(session.id())).isNull();
  }

  @Test
  void shouldIgnoreDeletionOfAnUnknownSession() {
    // when a session that was already swept is deleted again
    // then nothing is thrown
    sessionStore.delete(IdGenerator.getNextId());
  }

  @Test
  void shouldListEverySessionForTheExpirySweep() {
    // given
    final PersistentSession first = newSession();
    final PersistentSession second = newSession();
    sessionStore.upsert(first);
    sessionStore.upsert(second);

    // when
    // then CSL's deletion task sees both, which is what lets it evict the expired ones
    assertThat(sessionsInStore())
        .extracting(PersistentSession::id)
        .contains(first.id(), second.id());
  }

  private List<PersistentSession> sessionsInStore() {
    // getAll is a search, so the writes have to be visible to it first
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
    return sessionStore.getAll();
  }

  private static PersistentSession newSession() {
    final long now = System.currentTimeMillis();
    return new PersistentSession(
        IdGenerator.getNextId(),
        now,
        now,
        MAX_INACTIVE_INTERVAL_SECONDS,
        Map.of(ATTRIBUTE_NAME, ATTRIBUTE_VALUE));
  }
}
