/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.SecurityContext;

public interface GroupSearchClient {

  GroupEntity getGroup(final String id);

  SearchQueryResult<GroupEntity> searchGroups(final GroupQuery query);

  SearchQueryResult<GroupMemberEntity> searchGroupMembers(GroupQuery query);

  GroupSearchClient withSecurityContext(SecurityContext securityContext);
}
