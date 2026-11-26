/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.search.clients.reader.CaseReader;
import io.camunda.search.entities.CaseEntity;
import io.camunda.search.query.CaseQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.core.authz.ResourceAccessChecks;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class CaseDbReader implements CaseReader {

  private static final String NOT_SUPPORTED =
      "Case search is not yet supported on RDBMS, see camunda/camunda#66836";

  @Override
  public SearchQueryResult<CaseEntity> search(
      final CaseQuery query, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public @Nullable CaseEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }
}
