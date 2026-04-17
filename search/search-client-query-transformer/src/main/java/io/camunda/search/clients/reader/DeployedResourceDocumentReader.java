/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.DeployedResourceEntity;
import io.camunda.search.result.DeployedResourceQueryResultConfig;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class DeployedResourceDocumentReader extends DocumentBasedReader
    implements DeployedResourceReader {

  public DeployedResourceDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public DeployedResourceEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getById(
            String.valueOf(key),
            io.camunda.webapps.schema.entities.resource.DeployedResourceEntity.class,
            indexDescriptor.getFullQualifiedName(),
            DeployedResourceQueryResultConfig.of(b -> b.includeContent(true)));
  }

  @Override
  public DeployedResourceEntity getByKeyMetadata(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getById(
            String.valueOf(key),
            io.camunda.webapps.schema.entities.resource.DeployedResourceEntity.class,
            indexDescriptor.getFullQualifiedName(),
            DeployedResourceQueryResultConfig.of(b -> b.includeContent(false)));
  }
}
