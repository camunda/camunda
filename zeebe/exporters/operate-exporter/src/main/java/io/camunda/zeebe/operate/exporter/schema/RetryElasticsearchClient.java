/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.schema;

import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.PutComponentTemplateRequest;
import org.elasticsearch.client.indices.PutComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.PutMappingRequest;

public class RetryElasticsearchClient {

  public void createComponentTemplate(final PutComponentTemplateRequest request) {}

  public boolean createIndex(final CreateIndexRequest createIndexRequest) {
    return false;
  }

  public boolean createTemplate(
      final PutComposableIndexTemplateRequest request, final boolean overwrite) {
    return false;
  }

  public void putMapping(final PutMappingRequest request) {}
}
