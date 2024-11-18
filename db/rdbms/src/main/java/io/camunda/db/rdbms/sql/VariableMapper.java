/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.VariableQuery;
import java.util.List;

public interface VariableMapper {

  void insert(VariableDbModel variable);

  void update(VariableDbModel variable);

  Long count(VariableQuery filter);

  List<VariableEntity> search(VariableQuery filter);
}
