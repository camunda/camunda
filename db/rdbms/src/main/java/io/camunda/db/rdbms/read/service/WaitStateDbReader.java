/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.sql.WaitStateMapper;
import io.camunda.db.rdbms.write.domain.WaitStateDbModel;
import java.util.Optional;

/**
 * Reads wait-state rows by key.
 *
 * <p>Only key-based lookup is provided; the searchable read layer for waiting states is built
 * separately on top of the wait-state index.
 */
public class WaitStateDbReader {

  private final WaitStateMapper waitStateMapper;

  public WaitStateDbReader(final WaitStateMapper waitStateMapper) {
    this.waitStateMapper = waitStateMapper;
  }

  public Optional<WaitStateDbModel> findOne(final long waitStateKey) {
    return Optional.ofNullable(waitStateMapper.findOne(waitStateKey));
  }
}
