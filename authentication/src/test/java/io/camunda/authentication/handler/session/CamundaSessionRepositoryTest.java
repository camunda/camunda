/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.camunda.search.security.SessionDocumentStorageClient;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class CamundaSessionRepositoryTest {

  private final SessionDocumentMapper sessionDocumentMapper =
      new SessionDocumentMapper(new GenericConversionService());
  private final SessionDocumentStorageClient sessionDocumentStorageClient =
      new NoOpSessionDocumentStorageClient();

  private CamundaSessionRepository repository;

  @BeforeEach
  void setUp() {
    final ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
    repository =
        new CamundaSessionRepository(
            executor, sessionDocumentMapper, sessionDocumentStorageClient, null);
  }

  @Test
  void createSessionReturnSession() {
    final var session = repository.createSession();

    assertNotNull(session);
    assertThat(session.getId()).isNotNull();
    assertThat(session.getLastAccessedTime()).isNotNull();
    assertThat(session.getCreationTime()).isNotNull();
    assertThat(session.getMaxInactiveInterval()).isNotNull();
  }

  @Test
  void saveValidSessionPersistsSession() {
    final var session = repository.createSession();
    session.changeSessionId();
    assertThat(session.isChanged()).isTrue();

    repository.save(session);

    assertThat(session.isChanged()).isFalse();
    assertThat(repository.findById(session.getId())).isNotNull();
  }

  @Test
  void saveExpiredSessionDeleteSession() {
    final var session = repository.createSession();
    session.changeSessionId();
    assertThat(session.isChanged()).isTrue();
    repository.save(session);
    session.setLastAccessedTime(Instant.now().minusSeconds(3600));
    assertThat(session.isChanged()).isTrue();

    repository.save(session);

    assertThat(repository.findById(session.getId())).isNull();
  }

  @Test
  void findByNotExistingIdReturnsNull() {
    assertNull(repository.findById("not-existing-id"));
  }

  @Test
  void deleteById() {
    final var session = repository.createSession();
    session.changeSessionId();
    repository.save(session);
    assertThat(repository.findById(session.getId())).isNotNull();

    repository.deleteById(session.getId());
    assertThat(repository.findById(session.getId())).isNull();
  }
}
