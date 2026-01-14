/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.websession;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.read.service.PersistentWebSessionDbReader;
import io.camunda.db.rdbms.write.service.PersistentWebSessionWriter;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
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
    final PersistentWebSessionDbReader reader =
        testApplication.bean(PersistentWebSessionDbReader.class);
    final PersistentWebSessionWriter writer =
        testApplication.bean(PersistentWebSessionWriter.class);

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
    writer.upsert(session);

    // then
    final PersistentWebSessionEntity foundSession = reader.findById(sessionId);
    assertThat(foundSession).isNotNull();
    assertThat(foundSession.id()).isEqualTo(sessionId);
    assertThat(foundSession.creationTime()).isEqualTo(creationTime);
    assertThat(foundSession.lastAccessedTime()).isEqualTo(lastAccessedTime);
    assertThat(foundSession.maxInactiveIntervalInSeconds()).isEqualTo(maxInactiveInterval);
    assertThat(foundSession.attributes()).hasSize(2);
    assertThat(foundSession.attributes().get("username")).isEqualTo("testuser".getBytes());
    assertThat(foundSession.attributes().get("role")).isEqualTo("admin".getBytes());
  }

  @TestTemplate
  public void shouldUpdateExistingWebSession(final CamundaRdbmsTestApplication testApplication) {
    final PersistentWebSessionDbReader reader =
        testApplication.bean(PersistentWebSessionDbReader.class);
    final PersistentWebSessionWriter writer =
        testApplication.bean(PersistentWebSessionWriter.class);

    // given - create initial session
    final String sessionId = UUID.randomUUID().toString();
    final long creationTime = System.currentTimeMillis();
    final long initialAccessTime = creationTime + 1000;

    final Map<String, byte[]> initialAttributes = new HashMap<>();
    initialAttributes.put("counter", "1".getBytes());

    final PersistentWebSessionEntity initialSession =
        new PersistentWebSessionEntity(
            sessionId, creationTime, initialAccessTime, 1800L, initialAttributes);

    writer.upsert(initialSession);

    // when - update the session
    final long updatedAccessTime = creationTime + 5000;
    final Map<String, byte[]> updatedAttributes = new HashMap<>();
    updatedAttributes.put("counter", "5".getBytes());
    updatedAttributes.put("newAttribute", "value".getBytes());

    final PersistentWebSessionEntity updatedSession =
        new PersistentWebSessionEntity(
            sessionId, creationTime, updatedAccessTime, 1800L, updatedAttributes);

    writer.upsert(updatedSession);

    // then
    final PersistentWebSessionEntity foundSession = reader.findById(sessionId);
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
    final PersistentWebSessionDbReader reader =
        testApplication.bean(PersistentWebSessionDbReader.class);
    final PersistentWebSessionWriter writer =
        testApplication.bean(PersistentWebSessionWriter.class);

    // given - create a session
    final String sessionId = UUID.randomUUID().toString();
    final Map<String, byte[]> attributes = new HashMap<>();
    attributes.put("data", "test".getBytes());

    final PersistentWebSessionEntity session =
        new PersistentWebSessionEntity(
            sessionId, System.currentTimeMillis(), System.currentTimeMillis(), 1800L, attributes);

    writer.upsert(session);

    // verify it exists
    assertThat(reader.findById(sessionId)).isNotNull();

    // when - delete the session
    writer.deleteById(sessionId);

    // then - verify it's gone
    assertThat(reader.findById(sessionId)).isNull();
  }

  @TestTemplate
  public void shouldFindAllWebSessions(final CamundaRdbmsTestApplication testApplication) {
    final PersistentWebSessionDbReader reader =
        testApplication.bean(PersistentWebSessionDbReader.class);
    final PersistentWebSessionWriter writer =
        testApplication.bean(PersistentWebSessionWriter.class);

    // given - create multiple sessions
    final String sessionId1 = UUID.randomUUID().toString();
    final String sessionId2 = UUID.randomUUID().toString();
    final String sessionId3 = UUID.randomUUID().toString();

    final Map<String, byte[]> attributes = new HashMap<>();
    attributes.put("test", "data".getBytes());

    final long now = System.currentTimeMillis();

    writer.upsert(new PersistentWebSessionEntity(sessionId1, now, now, 1800L, attributes));
    writer.upsert(
        new PersistentWebSessionEntity(sessionId2, now + 1000, now + 1000, 1800L, attributes));
    writer.upsert(
        new PersistentWebSessionEntity(sessionId3, now + 2000, now + 2000, 1800L, attributes));

    // when
    final var allSessions = reader.findAll();

    // then - should contain at least our 3 sessions (may contain more from other tests)
    assertThat(allSessions).isNotNull();
    assertThat(allSessions.stream().map(PersistentWebSessionEntity::id))
        .contains(sessionId1, sessionId2, sessionId3);
  }

  @TestTemplate
  public void shouldHandleEmptyAttributes(final CamundaRdbmsTestApplication testApplication) {
    final PersistentWebSessionDbReader reader =
        testApplication.bean(PersistentWebSessionDbReader.class);
    final PersistentWebSessionWriter writer =
        testApplication.bean(PersistentWebSessionWriter.class);

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
    writer.upsert(session);

    // then
    final PersistentWebSessionEntity foundSession = reader.findById(sessionId);
    assertThat(foundSession).isNotNull();
    assertThat(foundSession.attributes()).isNotNull();
    assertThat(foundSession.attributes()).isEmpty();
  }

  @TestTemplate
  public void shouldHandleLargeAttributes(final CamundaRdbmsTestApplication testApplication) {
    final PersistentWebSessionDbReader reader =
        testApplication.bean(PersistentWebSessionDbReader.class);
    final PersistentWebSessionWriter writer =
        testApplication.bean(PersistentWebSessionWriter.class);

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
    writer.upsert(session);

    // then
    final PersistentWebSessionEntity foundSession = reader.findById(sessionId);
    assertThat(foundSession).isNotNull();
    assertThat(foundSession.attributes()).hasSize(2);
    assertThat(foundSession.attributes().get("largeAttribute")).isEqualTo(largeData);
    assertThat(foundSession.attributes().get("normalAttribute")).isEqualTo("small".getBytes());
  }
}
