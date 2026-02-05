/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import io.camunda.db.rdbms.write.domain.JobDbModel;

public class InsertJobMerger extends BatchInsertMerger<JobDbModel> {

  public InsertJobMerger(final JobDbModel job, final int maxBatchSize) {
    super(ContextType.JOB, job, maxBatchSize);
  }
}
