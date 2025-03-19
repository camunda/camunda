/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessDefinitionFlowNodeStatisticsEntity;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import java.util.List;

public interface ProcessDefinitionMapper {

  void insert(ProcessDefinitionDbModel processDeployment);

  Long count(ProcessDefinitionDbQuery filter);

  List<ProcessDefinitionEntity> search(ProcessDefinitionDbQuery filter);

  List<ProcessDefinitionFlowNodeStatisticsEntity> flowNodeStatistics(
      ProcessDefinitionStatisticsFilter filter);
}
