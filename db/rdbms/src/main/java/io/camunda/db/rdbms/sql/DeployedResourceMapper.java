/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.DeployedResourceDbQuery;
import io.camunda.db.rdbms.write.domain.DeployedResourceDbModel;
import io.camunda.search.entities.DeployedResourceEntity;
import java.util.List;

public interface DeployedResourceMapper {

  void insert(DeployedResourceDbModel resource);

  void delete(Long resourceKey);

  DeployedResourceEntity get(Long resourceKey);

  /** Like {@link #get} but omits the RESOURCE_CONTENT column. */
  DeployedResourceEntity getMetadata(Long resourceKey);

  Long count(DeployedResourceDbQuery filter);

  List<DeployedResourceEntity> search(DeployedResourceDbQuery filter);
}
