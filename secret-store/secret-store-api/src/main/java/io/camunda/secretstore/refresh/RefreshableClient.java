/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.refresh;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * A client built from configuration values, such as a database connection pool, that can take new
 * values while it runs. See {@link SwappableClient} for the usual way to implement it.
 */
public interface RefreshableClient {

  /** The name of the client in logs and metrics, such as {@code elasticsearch}. */
  String name();

  /**
   * The configuration properties the client is built from, relative to {@code camunda.}, such as
   * {@code data.secondary-storage.elasticsearch.password}.
   */
  Set<String> properties();

  /** The files the client reads, whose content can change at the same path. */
  default Set<Path> files() {
    return Set.of();
  }

  /**
   * Builds a new client from the given values and moves to it. If this fails, the current client
   * stays in use.
   *
   * @param values the current value of each of {@link #properties()}
   * @throws ClientRefreshException if the new client couldn't be built or failed its check
   */
  void refresh(Map<String, String> values);
}
