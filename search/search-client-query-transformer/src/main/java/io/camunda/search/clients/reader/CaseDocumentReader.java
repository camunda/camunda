/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.entities.CaseEntity;
import io.camunda.search.query.CaseQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.core.authz.ResourceAccessChecks;

public class CaseDocumentReader implements CaseReader {

  private static final String NOT_SUPPORTED =
      "Case search is not yet supported on Elasticsearch/OpenSearch, see camunda/camunda#66835";

  @Override
  public SearchQueryResult<CaseEntity> search(
      final CaseQuery query, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public CaseEntity getByKey(final long key, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }
}
