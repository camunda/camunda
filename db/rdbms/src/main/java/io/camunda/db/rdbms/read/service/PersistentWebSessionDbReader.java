/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.sql.PersistentWebSessionMapper;
import io.camunda.search.entities.PersistentWebSessionEntity;
import java.util.List;

public class PersistentWebSessionDbReader {

  private final PersistentWebSessionMapper persistentWebSessionMapper;

  public PersistentWebSessionDbReader(final PersistentWebSessionMapper persistentWebSessionMapper) {
    this.persistentWebSessionMapper = persistentWebSessionMapper;
  }

  public PersistentWebSessionEntity findById(final String sessionId) {
    return persistentWebSessionMapper.findById(sessionId);
  }

  public List<PersistentWebSessionEntity> findAll() {
    return persistentWebSessionMapper.findAll();
  }
}
