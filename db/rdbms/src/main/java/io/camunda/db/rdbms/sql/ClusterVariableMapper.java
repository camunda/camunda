/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.ClusterVariableDbQuery;
import io.camunda.db.rdbms.write.domain.ClusterVariableDbModel;
import io.camunda.db.rdbms.write.domain.ClusterVariableMetadataDbModel;
import io.camunda.search.entities.ClusterVariableEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ClusterVariableMapper {

  ClusterVariableEntity get(String id);

  void insert(ClusterVariableDbModel variable);

  void insertMetadata(ClusterVariableDbModel variable);

  void deleteMetadata(String id);

  void delete(ClusterVariableDbModel variable);

  Long count(ClusterVariableDbQuery filter);

  List<ClusterVariableEntity> search(ClusterVariableDbQuery filter);

  List<ClusterVariableMetadataDbModel> findMetadataByClusterVariableIds(
      @Param("ids") List<String> ids);
}
