/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.write.domain.ProcessDefinitionVariableNameLookupDbModel;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ProcessDefinitionVariableNameLookupMapper {

  /**
   * Inserts a new lookup entry if no entry already exists for the given (processDefinitionKey,
   * varName) combination. No-op on conflict.
   */
  void insertIfNotExists(ProcessDefinitionVariableNameLookupDbModel model);

  /** Returns all variable names recorded for the given process definition key. */
  List<String> findVariableNames(@Param("processDefinitionKey") long processDefinitionKey);

  /** Deletes all lookup entries for the given process definition keys. */
  void deleteByProcessDefinitionKeys(List<Long> processDefinitionKeys);
}
