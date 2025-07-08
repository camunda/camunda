/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.search.clients.reader.GroupMemberReader;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryResult;

public class GroupMemberDbReader extends AbstractEntityReader<GroupMemberReader>
    implements GroupMemberReader {

  public GroupMemberDbReader() {
    super(null);
  }

  @Override
  public GroupMemberEntity getByKey(
      final String key, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException("Group member get by key not implemented on RDBMS");
  }

  @Override
  public SearchQueryResult<GroupMemberEntity> search(
      final GroupQuery query, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException("Group member search not implemented on RDBMS");
  }
}
