/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.domain.ProcessInstanceFilter;
import io.camunda.db.rdbms.domain.ProcessInstanceModel;
import java.util.List;
import org.apache.ibatis.session.RowBounds;

public interface ProcessInstanceMapper {
  void insert(ProcessInstanceModel processInstance);

  ProcessInstanceModel findOne(Long processInstanceKey);

  Integer count(ProcessInstanceFilter filter);

  List<ProcessInstanceModel> search(ProcessInstanceFilter filter, RowBounds rowBounds);
}
