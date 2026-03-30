/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static io.camunda.db.rdbms.read.NullSafeStrings.nullToEmpty;

import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel;
import io.camunda.search.entities.GlobalListenerEntity;

public class GlobalListenerEntityMapper {
  public static GlobalListenerEntity toEntity(final GlobalListenerDbModel dbModel) {
    if (dbModel == null) {
      return null;
    }
    return new GlobalListenerEntity(
        dbModel.id(),
        nullToEmpty(dbModel.listenerId()),
        nullToEmpty(dbModel.type()),
        dbModel.eventTypes(),
        dbModel.retries(),
        dbModel.afterNonGlobal(),
        dbModel.priority(),
        dbModel.source(),
        dbModel.listenerType(),
        dbModel.elementTypes(),
        dbModel.categories());
  }
}
