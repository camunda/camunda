/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;

public class JobEntityMapper {

  public static JobEntity toEntity(final JobDbModel jobDbModel) {
    return new JobEntity(
        jobDbModel.jobKey(),
        jobDbModel.type(),
        jobDbModel.worker(),
        JobState.valueOf(jobDbModel.state().name()),
        JobKind.valueOf(jobDbModel.kind().name()),
        ListenerEventType.valueOf(jobDbModel.listenerEventType().name()),
        jobDbModel.retries(),
        jobDbModel.isDenied(),
        jobDbModel.deniedReason(),
        jobDbModel.hasFailedWithRetriesLeft(),
        jobDbModel.errorCode(),
        jobDbModel.errorMessage(),
        jobDbModel.customHeaders(),
        jobDbModel.deadline(),
        jobDbModel.endTime(),
        jobDbModel.processDefinitionId(),
        jobDbModel.processDefinitionKey(),
        jobDbModel.processInstanceKey(),
        jobDbModel.elementId(),
        jobDbModel.elementInstanceKey(),
        jobDbModel.tenantId());
  }
}
