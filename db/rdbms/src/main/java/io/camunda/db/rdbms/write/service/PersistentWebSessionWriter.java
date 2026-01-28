/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.PersistentWebSessionMapper;
import io.camunda.search.entities.PersistentWebSessionEntity;

public class PersistentWebSessionWriter implements RdbmsWriter {

  private final PersistentWebSessionMapper persistentWebSessionMapper;

  public PersistentWebSessionWriter(final PersistentWebSessionMapper persistentWebSessionMapper) {
    this.persistentWebSessionMapper = persistentWebSessionMapper;
  }

  public void upsert(final PersistentWebSessionEntity persistentWebSessionEntity) {
    persistentWebSessionMapper.upsert(persistentWebSessionEntity);
  }

  public void deleteById(final String sessionId) {
    persistentWebSessionMapper.deleteById(sessionId);
  }
}
