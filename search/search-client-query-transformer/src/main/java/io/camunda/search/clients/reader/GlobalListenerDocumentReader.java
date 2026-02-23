/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.entities.GlobalListenerType;
import io.camunda.search.query.GlobalListenerQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.util.GlobalListenerUtil;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class GlobalListenerDocumentReader extends DocumentBasedReader
    implements GlobalListenerReader {

  public GlobalListenerDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public SearchQueryResult<GlobalListenerEntity> search(
      final GlobalListenerQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.globallistener.GlobalListenerEntity.class,
            resourceAccessChecks);
  }

  @Override
  public GlobalListenerEntity getGlobalListener(
      final String listenerId,
      final GlobalListenerType listenerType,
      final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getById(
            GlobalListenerUtil.generateId(listenerId, listenerType),
            io.camunda.webapps.schema.entities.globallistener.GlobalListenerEntity.class,
            indexDescriptor.getFullQualifiedName());
  }
}
