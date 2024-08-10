/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.exceptions.OperateRuntimeException;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ImportBatchProcessorFactory {

  @Autowired private List<ImportBatchProcessor> importBatchProcessors;

  private final Map<String, ImportBatchProcessor> processorsMap = new HashMap<>();

  @PostConstruct
  private void buildTheMap() {
    for (final ImportBatchProcessor importBatchProcessor : importBatchProcessors) {
      processorsMap.put(importBatchProcessor.getZeebeVersion(), importBatchProcessor);
    }
  }

  public ImportBatchProcessor getImportBatchProcessor(String zeebeVersion) {
    // search for exact version match
    ImportBatchProcessor processor = processorsMap.get(zeebeVersion);
    if (processor == null) {
      // search for minor version match
      zeebeVersion = zeebeVersion.substring(0, zeebeVersion.lastIndexOf("."));
      processor = processorsMap.get(zeebeVersion);
    }
    if (processor == null) {
      throw new OperateRuntimeException(
          String.format("Import is not possible for Zeebe version: %s", zeebeVersion));
    }
    return processor;
  }
}
