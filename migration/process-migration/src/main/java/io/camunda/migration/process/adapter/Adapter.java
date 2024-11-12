/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process.adapter;

import io.camunda.migration.api.MigrationException;
import io.camunda.operate.schema.migration.ProcessorStep;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface Adapter {

  String PROCESSOR_STEP_ID = VersionUtil.getVersion() + "-1";
  String PROCESSOR_STEP_TYPE = "processorStep";
  String PROCESS_DEFINITION_KEY = "key";
  String STEP_DESCRIPTION = "Process Migration last migrated document";

  String migrate(List<ProcessEntity> records) throws MigrationException;

  List<ProcessEntity> nextBatch(final String processDefinitionKey);

  String readLastMigratedEntity() throws MigrationException;

  void writeLastMigratedEntity(final String processDefinitionKey) throws MigrationException;

  void close() throws IOException;

  default Map<String, Object> getUpdateMap(final ProcessEntity entity) {
    final Map<String, Object> updateMap = new HashMap<>();
    updateMap.put(ProcessIndex.IS_PUBLIC, entity.getIsPublic());

    if (entity.getFormId() != null) {
      updateMap.put(ProcessIndex.FORM_ID, entity.getFormId());
    }
    return updateMap;
  }

  default ProcessorStep upsertProcessorStep(final String processDefinitionKey) {
    final ProcessorStep step = new ProcessorStep();
    step.setContent(processDefinitionKey);
    step.setApplied(true);
    step.setIndexName(ProcessIndex.INDEX_NAME);
    step.setDescription(STEP_DESCRIPTION);
    step.setVersion(VersionUtil.getVersion());
    return step;
  }
}
