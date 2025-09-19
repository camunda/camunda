/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import io.camunda.search.schema.utils.CloneResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface PrefixMigrationClient {

  List<String> getIndicesInAlias(String alias);

  CompletableFuture<CloneResult> cloneAndDeleteIndex(
      final String source,
      final String sourceAlias,
      final String destination,
      final String destinationAlias);

  CompletableFuture<Void> deleteIndex(String... indexName);

  CompletableFuture<Void> deleteComponentTemplate(String componentTemplateName);

  CompletableFuture<Void> deleteIndexTemplate(String indexTemplateName);
}
