/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.domain.VariableModel;
import java.util.List;

public interface VariableMapper {

  void insert(VariableModel variable);

  void update(VariableModel variable);

  VariableModel findOne(Long key);

  boolean exists(Long key);

  List<VariableModel> find(VariableFilter filter);

  record VariableFilter(Long processInstanceKey) {}
}
