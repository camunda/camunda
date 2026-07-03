/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

/**
 * The <b>schema</b> seam of a document backend — the third peer to {@link
 * DocumentBasedSearchClient} (read) and {@link DocumentBasedWriteClient} (write), covering index
 * and mapping provisioning. A backend (Elasticsearch/OpenSearch) provides a thin implementation;
 * callers depend on this interface so index provisioning is backend-neutral, just like read and
 * write.
 */
public interface DocumentBasedSchemaClient {

  /**
   * Creates {@code index} with the given JSON mapping if it does not already exist (idempotent).
   */
  void createIndex(String index, String mappingJson);
}
