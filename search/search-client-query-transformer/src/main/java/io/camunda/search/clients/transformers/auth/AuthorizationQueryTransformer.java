/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.auth;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;

/** Creates an authorization filter query */
public interface AuthorizationQueryTransformer {

  SearchQuery toSearchQuery(
      AuthorizationResourceType resourceType,
      PermissionType permissionType,
      List<String> resourceKeys);
}
