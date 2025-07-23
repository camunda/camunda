/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.UsageMetricsTUEntity;

public class UsageMetricsTUEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.metrics.UsageMetricsTUEntity, UsageMetricsTUEntity> {

  @Override
  public UsageMetricsTUEntity apply(
      final io.camunda.webapps.schema.entities.metrics.UsageMetricsTUEntity value) {
    return new UsageMetricsTUEntity(
        value.getId(), value.getTenantId(), value.getEventTime(), value.getAssigneeHash());
  }
}
