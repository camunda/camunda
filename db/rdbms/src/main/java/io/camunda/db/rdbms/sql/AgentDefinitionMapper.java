/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.AgentDefinitionDbQuery;
import io.camunda.db.rdbms.write.domain.AgentDefinitionDbModel;
import java.util.List;

public interface AgentDefinitionMapper {

  void insert(AgentDefinitionDbModel agentDefinition);

  Long count(AgentDefinitionDbQuery query);

  List<AgentDefinitionDbModel> search(AgentDefinitionDbQuery query);

  int deleteByProcessDefinitionKeys(List<Long> processDefinitionKeys, int limit);
}
