/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static io.camunda.db.rdbms.read.NullSafeStrings.nullToEmpty;

import io.camunda.db.rdbms.write.domain.CorrelatedMessageSubscriptionDbModel;
import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity;

public class CorrelatedMessageSubscriptionEntityMapper {

  public static CorrelatedMessageSubscriptionEntity toEntity(
      final CorrelatedMessageSubscriptionDbModel dbModel) {
    return CorrelatedMessageSubscriptionEntity.builder()
        .correlationKey(nullToEmpty(dbModel.correlationKey()))
        .correlationTime(dbModel.correlationTime())
        .flowNodeId(nullToEmpty(dbModel.flowNodeId()))
        .flowNodeInstanceKey(dbModel.flowNodeInstanceKey())
        .messageKey(dbModel.messageKey())
        .messageName(nullToEmpty(dbModel.messageName()))
        .partitionId(dbModel.partitionId())
        .processDefinitionId(nullToEmpty(dbModel.processDefinitionId()))
        .processDefinitionKey(dbModel.processDefinitionKey())
        .processInstanceKey(dbModel.processInstanceKey())
        .rootProcessInstanceKey(dbModel.rootProcessInstanceKey())
        .subscriptionKey(dbModel.subscriptionKey())
        .subscriptionType(dbModel.subscriptionType())
        .tenantId(nullToEmpty(dbModel.tenantId()))
        .build();
  }
}
