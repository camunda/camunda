/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.domain.ProcessInstanceModel;
import java.util.List;

public interface ProcessInstanceMapper {
  record ProcessInstanceFilter(
      String processInstanceKey
  ) {}

  void insert(ProcessInstanceModel processInstance);

  ProcessInstanceModel findOne(Long processInstanceKey);

  List<ProcessInstanceModel> find(ProcessInstanceFilter filter);
}
