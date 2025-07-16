/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.search.clients.reader.RoleMemberReader;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;

public class RoleMemberDbReader extends AbstractEntityReader<RoleMemberEntity>
    implements RoleMemberReader {

  public RoleMemberDbReader() {
    super(null);
  }

  @Override
  public SearchQueryResult<RoleMemberEntity> search(
      final RoleQuery query, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException("Role member search not implemented on RDBMS");
  }
}
