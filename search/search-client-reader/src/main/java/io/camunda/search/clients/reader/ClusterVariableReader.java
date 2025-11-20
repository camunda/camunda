/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.security.reader.ResourceAccessChecks;

public interface ClusterVariableReader
    extends SearchEntityReader<ClusterVariableEntity, ClusterVariableQuery> {

  ClusterVariableEntity getTenantScopedClusterVariable(
      String name, String tenant, ResourceAccessChecks resourceAccessChecks);

  ClusterVariableEntity getGloballyScopedClusterVariable(
      String name, ResourceAccessChecks resourceAccessChecks);
}
