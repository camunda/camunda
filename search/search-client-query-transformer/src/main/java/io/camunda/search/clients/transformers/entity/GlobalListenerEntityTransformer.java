/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.entities.GlobalListenerSource;
import io.camunda.search.entities.GlobalListenerType;

public class GlobalListenerEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.globallistener.GlobalListenerEntity,
        GlobalListenerEntity> {

  @Override
  public GlobalListenerEntity apply(
      final io.camunda.webapps.schema.entities.globallistener.GlobalListenerEntity value) {
    return new GlobalListenerEntity(
        value.getId(),
        value.getListenerId(),
        value.getType(),
        value.getEventTypes(),
        value.getRetries(),
        value.isAfterNonGlobal(),
        value.getPriority(),
        GlobalListenerSource.valueOf(value.getSource().name()),
        GlobalListenerType.valueOf(value.getListenerType().name()));
  }
}
