/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.store;

/**
 * One backend's serving store: its three narrow seams bundled together — {@link
 * DatasetSchemaManager} (provision), {@link DatasetWriter} (upsert), {@link DatasetQueryClient}
 * (fetch). A backend module (RDBMS / Elasticsearch / OpenSearch) provides one implementation;
 * selection by {@code DatabaseType} lives in the wiring layer that knows the backends, keeping this
 * engine module backend-neutral.
 */
public interface DatasetStore extends AutoCloseable {

  DatasetSchemaManager schemaManager();

  DatasetWriter writer();

  DatasetQueryClient queryClient();

  @Override
  void close();
}
