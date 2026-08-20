/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.websession;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.identity.PhysicalTenantScopedPersistentWebSessionClientFactory;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class PersistentWebSessionIT {

  @TestTemplate
  public void shouldSaveAndFindWebSessionById(final CamundaRdbmsTestApplication testApplication) {
    final var persistentWebSessionClient = getPersistentWebSessionClient(testApplication);

    // given
    final String sessionId = UUID.randomUUID().toString();
    final long creationTime = System.currentTimeMillis();
    final long lastAccessedTime = creationTime + 1000;
    final long maxInactiveInterval = 1800L; // 30 minutes

    final Map<String, byte[]> attributes = new HashMap<>();
    attributes.put("username", "testuser".getBytes());
    attributes.put("role", "admin".getBytes());

    final PersistentWebSessionEntity session =
        new PersistentWebSessionEntity(
            sessionId, creationTime, lastAccessedTime, maxInactiveInterval, attributes);

    // when
    persistentWebSessionClient.upsertPersistentWebSession(session);

    // then
    final PersistentWebSessionEntity foundSession =
        persistentWebSessionClient.getPersistentWebSession(sessionId);
    assertThat(foundSession).isNotNull();
    assertThat(foundSession.id()).isEqualTo(sessionId);
    assertThat(foundSession.creationTime()).isEqualTo(creationTime);
    assertThat(foundSession.lastAccessedTime()).isEqualTo(lastAccessedTime);
    assertThat(foundSession.maxInactiveIntervalInSeconds()).isEqualTo(maxInactiveInterval);
    assertThat(foundSession.attributes()).hasSize(2);
    assertThat(foundSession.attributes().get("username")).isEqualTo("testuser".getBytes());
    assertThat(foundSession.attributes().get("role")).isEqualTo("admin".getBytes());
  }

  private PersistentWebSessionClient getPersistentWebSessionClient(
      final CamundaRdbmsTestApplication testApplication) {
    return PhysicalTenantScopedPersistentWebSessionClientFactory.fromRdbmsMapperBundles(
            (testApplication.bean("rdbmsMapperBundles")))
        .withPhysicalTenant(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @TestTemplate
  public void shouldUpdateExistingWebSession(final CamundaRdbmsTestApplication testApplication) {
    final var persistentWebSessionClient = getPersistentWebSessionClient(testApplication);

    // given - create initial session
    final String sessionId = UUID.randomUUID().toString();
    final long creationTime = System.currentTimeMillis();
    final long initialAccessTime = creationTime + 1000;

    final Map<String, byte[]> initialAttributes = new HashMap<>();
    initialAttributes.put("counter", "1".getBytes());

    final PersistentWebSessionEntity initialSession =
        new PersistentWebSessionEntity(
            sessionId, creationTime, initialAccessTime, 1800L, initialAttributes);

    persistentWebSessionClient.upsertPersistentWebSession(initialSession);

    // when - update the session
    final long updatedAccessTime = creationTime + 5000;
    final Map<String, byte[]> updatedAttributes = new HashMap<>();
    updatedAttributes.put("counter", "5".getBytes());
    updatedAttributes.put("newAttribute", "value".getBytes());

    final PersistentWebSessionEntity updatedSession =
        new PersistentWebSessionEntity(
            sessionId, creationTime, updatedAccessTime, 1800L, updatedAttributes);

    persistentWebSessionClient.upsertPersistentWebSession(updatedSession);

    // then
    final PersistentWebSessionEntity foundSession =
        persistentWebSessionClient.getPersistentWebSession(sessionId);
    assertThat(foundSession).isNotNull();
    assertThat(foundSession.id()).isEqualTo(sessionId);
    assertThat(foundSession.creationTime()).isEqualTo(creationTime);
    assertThat(foundSession.lastAccessedTime()).isEqualTo(updatedAccessTime);
    assertThat(foundSession.attributes()).hasSize(2);
    assertThat(foundSession.attributes().get("counter")).isEqualTo("5".getBytes());
    assertThat(foundSession.attributes().get("newAttribute")).isEqualTo("value".getBytes());
  }

  @TestTemplate
  public void shouldDeleteWebSession(final CamundaRdbmsTestApplication testApplication) {
    final var persistentWebSessionClient = getPersistentWebSessionClient(testApplication);

    // given - create a session
    final String sessionId = UUID.randomUUID().toString();
    final Map<String, byte[]> attributes = new HashMap<>();
    attributes.put("data", "test".getBytes());

    final PersistentWebSessionEntity session =
        new PersistentWebSessionEntity(
            sessionId, System.currentTimeMillis(), System.currentTimeMillis(), 1800L, attributes);

    persistentWebSessionClient.upsertPersistentWebSession(session);

    // verify it exists
    assertThat(persistentWebSessionClient.getPersistentWebSession(sessionId)).isNotNull();

    // when - delete the session
    persistentWebSessionClient.deletePersistentWebSession(sessionId);

    // then - verify it's gone
    assertThat(persistentWebSessionClient.getPersistentWebSession(sessionId)).isNull();
  }

  @TestTemplate
  public void shouldFindAllWebSessions(final CamundaRdbmsTestApplication testApplication) {
    final var persistentWebSessionClient = getPersistentWebSessionClient(testApplication);

    // given - create multiple sessions
    final String sessionId1 = UUID.randomUUID().toString();
    final String sessionId2 = UUID.randomUUID().toString();
    final String sessionId3 = UUID.randomUUID().toString();

    final Map<String, byte[]> attributes = new HashMap<>();
    attributes.put("test", "data".getBytes());

    final long now = System.currentTimeMillis();

    persistentWebSessionClient.upsertPersistentWebSession(
        new PersistentWebSessionEntity(sessionId1, now, now, 1800L, attributes));
    persistentWebSessionClient.upsertPersistentWebSession(
        new PersistentWebSessionEntity(sessionId2, now + 1000, now + 1000, 1800L, attributes));
    persistentWebSessionClient.upsertPersistentWebSession(
        new PersistentWebSessionEntity(sessionId3, now + 2000, now + 2000, 1800L, attributes));

    // when
    final var allSessions = persistentWebSessionClient.getAllPersistentWebSessions().items();

    // then - should contain at least our 3 sessions (may contain more from other tests)
    assertThat(allSessions).isNotNull();
    assertThat(allSessions.stream().map(PersistentWebSessionEntity::id))
        .contains(sessionId1, sessionId2, sessionId3);
  }

  @TestTemplate
  public void shouldHandleEmptyAttributes(final CamundaRdbmsTestApplication testApplication) {
    final var persistentWebSessionClient = getPersistentWebSessionClient(testApplication);

    // given - session with empty attributes
    final String sessionId = UUID.randomUUID().toString();
    final Map<String, byte[]> emptyAttributes = new HashMap<>();

    final PersistentWebSessionEntity session =
        new PersistentWebSessionEntity(
            sessionId,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            1800L,
            emptyAttributes);

    // when
    persistentWebSessionClient.upsertPersistentWebSession(session);

    // then
    final PersistentWebSessionEntity foundSession =
        persistentWebSessionClient.getPersistentWebSession(sessionId);
    assertThat(foundSession).isNotNull();
    assertThat(foundSession.attributes()).isNotNull();
    assertThat(foundSession.attributes()).isEmpty();
  }

  @TestTemplate
  public void shouldHandleLargeAttributes(final CamundaRdbmsTestApplication testApplication) {
    final var persistentWebSessionClient = getPersistentWebSessionClient(testApplication);

    // given - session with large attribute values
    final String sessionId = UUID.randomUUID().toString();
    final Map<String, byte[]> attributes = new HashMap<>();

    // Create a large byte array (10KB)
    final byte[] largeData = new byte[10 * 1024];
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) (i % 256);
    }
    attributes.put("largeAttribute", largeData);
    attributes.put("normalAttribute", "small".getBytes());

    final PersistentWebSessionEntity session =
        new PersistentWebSessionEntity(
            sessionId, System.currentTimeMillis(), System.currentTimeMillis(), 1800L, attributes);

    // when
    persistentWebSessionClient.upsertPersistentWebSession(session);

    // then
    final PersistentWebSessionEntity foundSession =
        persistentWebSessionClient.getPersistentWebSession(sessionId);
    assertThat(foundSession).isNotNull();
    assertThat(foundSession.attributes()).hasSize(2);
    assertThat(foundSession.attributes().get("largeAttribute")).isEqualTo(largeData);
    assertThat(foundSession.attributes().get("normalAttribute")).isEqualTo("small".getBytes());
  }
}
