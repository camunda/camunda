/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process.util;

import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import java.util.List;

public class MigrationUtil {

  public static List<ProcessEntity> extractBatchData(final List<ProcessEntity> processDefinitions) {
    return processDefinitions.stream().map(MigrationUtil::map).toList();
  }

  private static ProcessEntity map(final ProcessEntity entity) {
    final ProcessEntity processEntity = new ProcessEntity();
    processEntity.setId(entity.getId());
    processEntity.setBpmnProcessId(entity.getBpmnProcessId());

    ProcessModelUtil.processStartEvent(entity.getBpmnXml().getBytes(), entity.getBpmnProcessId())
        .ifPresent(
            e -> {
              processEntity.setFormId(ProcessModelUtil.extractFormId(e).orElse(null));
              processEntity.setIsPublic(ProcessModelUtil.extractIsPublic(e).orElse(false));
              final String formKey = ProcessModelUtil.extractFormKey(e).orElse(null);
              processEntity.setFormKey(formKey);
              processEntity.setIsFormEmbedded(formKey != null);
            });
    return processEntity;
  }
}
