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
    return new JobEntity.Builder()
        .jobKey(jobDbModel.jobKey())
        .type(jobDbModel.type())
        .worker(jobDbModel.worker())
        .state(JobState.valueOf(jobDbModel.state().name()))
        .kind(JobKind.valueOf(jobDbModel.kind().name()))
        .listenerEventType(ListenerEventType.valueOf(jobDbModel.listenerEventType().name()))
        .retries(jobDbModel.retries())
        .isDenied(jobDbModel.isDenied())
        .deniedReason(jobDbModel.deniedReason())
        .hasFailedWithRetriesLeft(jobDbModel.hasFailedWithRetriesLeft())
        .errorCode(jobDbModel.errorCode())
        .errorMessage(jobDbModel.errorMessage())
        .customHeaders(jobDbModel.customHeaders())
        .deadline(jobDbModel.deadline())
        .endTime(jobDbModel.endTime())
        .processDefinitionId(jobDbModel.processDefinitionId())
        .processDefinitionKey(jobDbModel.processDefinitionKey())
        .processInstanceKey(jobDbModel.processInstanceKey())
        .elementId(jobDbModel.elementId())
        .elementInstanceKey(jobDbModel.elementInstanceKey())
        .tenantId(jobDbModel.tenantId())
        .creationTime(jobDbModel.creationTime())
        .lastUpdateTime(jobDbModel.lastUpdateTime())
        .build();
  }
}
