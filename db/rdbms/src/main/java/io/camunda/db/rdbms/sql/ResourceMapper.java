/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.ResourceDbQuery;
import io.camunda.db.rdbms.write.domain.ResourceDbModel;
import io.camunda.search.entities.ResourceEntity;
import java.util.List;

public interface ResourceMapper {

  void insert(ResourceDbModel resource);

  void delete(Long resourceKey);

  Long count(ResourceDbQuery filter);

  List<ResourceEntity> search(ResourceDbQuery filter);
}
