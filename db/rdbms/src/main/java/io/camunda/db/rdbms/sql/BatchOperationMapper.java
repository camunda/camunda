/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import java.util.List;

public interface BatchOperationMapper {

  void insert(BatchOperationDbModel batchOperationDbModel);

  Long count();

  List<BatchOperationEntity> search();
}
