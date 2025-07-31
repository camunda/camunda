/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.JobDbQuery;
import io.camunda.db.rdbms.write.domain.JobDbModel;
import java.util.List;

public interface JobMapper extends ProcessBasedHistoryCleanupMapper {

  void insert(JobDbModel job);

  void update(JobDbModel job);

  Long count(JobDbQuery filter);

  List<JobDbModel> search(JobDbQuery filter);
}
