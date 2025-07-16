/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.search.clients.reader.TenantMemberReader;
import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.reader.ResourceAccessChecks;

public class TenantMemberDbReader extends AbstractEntityReader<TenantMemberEntity>
    implements TenantMemberReader {

  public TenantMemberDbReader() {
    super(null);
  }

  @Override
  public SearchQueryResult<TenantMemberEntity> search(
      final TenantQuery query, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException("Tenant member search not implemented on RDBMS");
  }
}
