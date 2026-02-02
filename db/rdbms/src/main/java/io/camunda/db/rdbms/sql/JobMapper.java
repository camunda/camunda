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
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import io.camunda.util.ObjectBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public interface JobMapper extends ProcessInstanceDependantMapper {

  void insert(BatchInsertJobsDto dto);

  void update(JobDbModel job);

  Long count(JobDbQuery filter);

  List<JobDbModel> search(JobDbQuery filter);

  record BatchInsertJobsDto(List<JobDbModel> jobs)
      implements BatchInsertDto<BatchInsertJobsDto, JobDbModel> {

    @Override
    public BatchInsertJobsDto withAdditionalDbModel(final JobDbModel job) {
      return new Builder().jobs(new ArrayList<>(jobs)).job(job).build();
    }

    @Override
    public BatchInsertJobsDto copy(
        final Function<ObjectBuilder<BatchInsertJobsDto>, ObjectBuilder<BatchInsertJobsDto>>
            copyFunction) {
      return copyFunction.apply(new Builder().jobs(new ArrayList<>(jobs))).build();
    }

    public static class Builder implements ObjectBuilder<BatchInsertJobsDto> {

      private List<JobDbModel> jobs = new ArrayList<>();

      public Builder job(final JobDbModel job) {
        jobs.add(job);
        return this;
      }

      public Builder jobs(final List<JobDbModel> jobs) {
        this.jobs = jobs;
        return this;
      }

      @Override
      public BatchInsertJobsDto build() {
        return new BatchInsertJobsDto(jobs);
      }
    }
  }
}
