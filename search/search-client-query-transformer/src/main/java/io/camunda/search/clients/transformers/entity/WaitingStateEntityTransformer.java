/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.webapps.schema.entities.WaitingStateEntity;

public class WaitingStateEntityTransformer
    implements ServiceTransformer<
        WaitingStateEntity, io.camunda.search.entities.WaitingStateEntity> {

  @Override
  public io.camunda.search.entities.WaitingStateEntity apply(final WaitingStateEntity value) {
    return new io.camunda.search.entities.WaitingStateEntity(
        value.getElementInstanceKey(),
        value.getProcessInstanceKey(),
        value.getElementType(),
        value.getDetails(),
        value.getTenantId());
  }
}
