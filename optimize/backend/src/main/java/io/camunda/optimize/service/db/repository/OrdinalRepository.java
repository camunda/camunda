/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import java.util.Map;

/**
 * Repository interface for loading ordinal entries from the database. Used by {@link
 * io.camunda.optimize.service.importing.zeebe.cache.OrdinalCache} on startup.
 */
public interface OrdinalRepository {

  /**
   * Returns a map of ordinal value (int) to epoch-millis timestamp for every document stored in the
   * ordinal index.
   */
  Map<Integer, Long> loadAllOrdinals();
}
