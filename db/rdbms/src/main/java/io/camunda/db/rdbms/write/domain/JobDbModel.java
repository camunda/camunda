/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.function.Function;

public record JobDbModel(
    Long jobKey,
    Long processInstanceKey,
    Integer retries,
    String state,
    int partitionId,
    OffsetDateTime historyCleanupDate)
    implements DbModel<JobDbModel> {

  @Override
  public JobDbModel copy(
      final Function<ObjectBuilder<JobDbModel>, ObjectBuilder<JobDbModel>> copyFunction) {
    return copyFunction
        .apply(
            new Builder()
                .jobKey(jobKey)
                .processInstanceKey(processInstanceKey)
                .retries(retries)
                .partitionId(partitionId)
                .historyCleanupDate(historyCleanupDate))
        .build();
  }

  public static class Builder implements ObjectBuilder<JobDbModel> {

    private Long jobKey;
    private Long processInstanceKey;
    private Integer retries;
    private String state;
    private int partitionId;
    private OffsetDateTime historyCleanupDate;

    public Builder jobKey(final Long jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    public Builder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder retries(final int retries) {
      this.retries = retries;
      return this;
    }

    public Builder state(final String state) {
      this.state = state;
      return this;
    }

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public Builder historyCleanupDate(final OffsetDateTime value) {
      historyCleanupDate = value;
      return this;
    }

    @Override
    public JobDbModel build() {
      return new JobDbModel(
          jobKey, processInstanceKey, retries, state, partitionId, historyCleanupDate);
    }
  }
}
