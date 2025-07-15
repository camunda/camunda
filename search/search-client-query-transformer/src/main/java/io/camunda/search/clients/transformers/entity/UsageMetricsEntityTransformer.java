/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.UsageMetricsEntity;

public class UsageMetricsEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity, UsageMetricsEntity> {

  @Override
  public UsageMetricsEntity apply(
      final io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity value) {
    return new UsageMetricsEntity(
        value.getId(),
        value.getEventTime(),
        value.getEventType().toString(),
        value.getEventValue().toString());
  }
}
