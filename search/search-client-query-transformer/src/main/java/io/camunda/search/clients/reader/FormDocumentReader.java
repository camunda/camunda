/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;

public class FormDocumentReader extends DocumentBasedReader implements FormReader {

  public FormDocumentReader(final SearchClientBasedQueryExecutor executor) {
    super(executor);
  }

  @Override
  public SearchQueryResult<FormEntity> search(
      final FormQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query, io.camunda.webapps.schema.entities.form.FormEntity.class, resourceAccessChecks);
  }
}
