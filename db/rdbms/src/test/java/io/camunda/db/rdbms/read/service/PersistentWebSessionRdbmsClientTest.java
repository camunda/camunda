/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.PersistentWebSessionMapper;
import io.camunda.db.rdbms.write.service.PersistentWebSessionWriter;
import io.camunda.search.entities.PersistentWebSessionEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PersistentWebSessionRdbmsClientTest {

  private PersistentWebSessionMapper mapper;
  private PersistentWebSessionDbReader dbReader;
  private PersistentWebSessionWriter dbWriter;
  private PersistentWebSessionRdbmsClient client;

  @BeforeEach
  void setUp() {
    mapper = mock(PersistentWebSessionMapper.class);
    dbReader = new PersistentWebSessionDbReader(mapper);
    dbWriter = new PersistentWebSessionWriter(mapper);
    client = new PersistentWebSessionRdbmsClient(dbReader, dbWriter);
  }

  @Test
  void shouldGetPersistentWebSession() {
    // given
    final String sessionId = "test-session-id";
    final Map<String, byte[]> attributes = new HashMap<>();
    attributes.put("key", "value".getBytes());
    final PersistentWebSessionEntity entity =
        new PersistentWebSessionEntity(sessionId, 1000L, 2000L, 3600L, attributes);
    when(mapper.findById(sessionId)).thenReturn(entity);

    // when
    final PersistentWebSessionEntity result = client.getPersistentWebSession(sessionId);

    // then
    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(sessionId);
    assertThat(result.creationTime()).isEqualTo(1000L);
    assertThat(result.lastAccessedTime()).isEqualTo(2000L);
    assertThat(result.maxInactiveIntervalInSeconds()).isEqualTo(3600L);
    assertThat(result.attributes()).containsKey("key");
    verify(mapper).findById(sessionId);
  }

  @Test
  void shouldUpsertPersistentWebSession() {
    // given
    final String sessionId = "test-session-id";
    final Map<String, byte[]> attributes = new HashMap<>();
    final PersistentWebSessionEntity entity =
        new PersistentWebSessionEntity(sessionId, 1000L, 2000L, 3600L, attributes);

    // when
    client.upsertPersistentWebSession(entity);

    // then
    verify(mapper).upsert(entity);
  }

  @Test
  void shouldDeletePersistentWebSession() {
    // given
    final String sessionId = "test-session-id";

    // when
    client.deletePersistentWebSession(sessionId);

    // then
    verify(mapper).deleteById(sessionId);
  }

  @Test
  void shouldGetAllPersistentWebSessions() {
    // given
    final Map<String, byte[]> attributes = new HashMap<>();
    final List<PersistentWebSessionEntity> entities =
        List.of(
            new PersistentWebSessionEntity("session1", 1000L, 2000L, 3600L, attributes),
            new PersistentWebSessionEntity("session2", 1500L, 2500L, 3600L, attributes));
    when(mapper.findAll()).thenReturn(entities);

    // when
    final var result = client.getAllPersistentWebSessions();

    // then
    assertThat(result).isNotNull();
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).id()).isEqualTo("session1");
    assertThat(result.items().get(1).id()).isEqualTo("session2");
    verify(mapper).findAll();
  }
}
