/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

/**
 * The neutral <b>schema</b> seam for document backends — the third peer to OC's {@code
 * DocumentBasedSearchClient} (read) and {@code DocumentBasedWriteClient} (write), covering the one
 * operation those don't: index + mapping creation. A backend (Elasticsearch/OpenSearch) provides a
 * thin implementation; the stores above depend on this interface, so index provisioning is
 * backend-neutral just like read and write.
 */
public interface DocumentSchemaClient {

  /**
   * Creates {@code index} with the given JSON mapping if it does not already exist (idempotent).
   */
  void createIndex(String index, String mappingJson);
}
