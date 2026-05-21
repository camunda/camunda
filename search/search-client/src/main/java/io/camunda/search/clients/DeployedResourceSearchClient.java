/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.entities.DeployedResourceEntity;
import io.camunda.search.query.DeployedResourceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.SecurityContext;

public interface DeployedResourceSearchClient {

  DeployedResourceEntity getDeployedResource(long key);

  /** Like {@link #getDeployedResource} but omits the resource content payload. */
  DeployedResourceEntity getDeployedResourceMetadata(long key);

  SearchQueryResult<DeployedResourceEntity> searchDeployedResources(DeployedResourceQuery query);

  DeployedResourceSearchClient withSecurityContext(SecurityContext securityContext);
}
