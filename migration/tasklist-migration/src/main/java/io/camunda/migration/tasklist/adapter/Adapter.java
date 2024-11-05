/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.tasklist.adapter;

import io.camunda.tasklist.entities.ProcessEntity;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface Adapter {

  boolean migrate(List<io.camunda.webapps.schema.entities.operate.ProcessEntity> records);

  List<ProcessEntity> nextBatch();

  void close() throws IOException;

  default Map<String, Object> getUpdateMap(
      final io.camunda.webapps.schema.entities.operate.ProcessEntity entity) {
    final Map<String, Object> updateMap = new HashMap<>();
    updateMap.put(
        io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex.IS_PUBLIC,
        entity.getIsPublic());

    if (entity.getFormId() != null) {
      updateMap.put(
          io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex.FORM_ID,
          entity.getFormId());
    }
    return updateMap;
  }
}
