/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate34To35;

import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.plan.indices.EventProcessDefinitionIndexV3Old;
import org.camunda.optimize.upgrade.plan.indices.ProcessDefinitionIndexV4Old;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.Arrays;

public class AbstractUpgrade34IT extends AbstractUpgradeIT {
  protected static final String FROM_VERSION = "3.4.0";

  protected static final ProcessDefinitionIndexV4Old PROCESS_DEFINITION_INDEX = new ProcessDefinitionIndexV4Old();
  protected static final EventProcessDefinitionIndexV3Old EVENT_PROCESS_DEFINITION_INDEX = new EventProcessDefinitionIndexV3Old();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(
      Arrays.asList(
        PROCESS_DEFINITION_INDEX,
        EVENT_PROCESS_DEFINITION_INDEX
      )
    );
    setMetadataVersion(FROM_VERSION);
  }

  protected boolean indexExists(final IndexMappingCreator index) {
    final GetIndexRequest request = new GetIndexRequest(indexNameService.getOptimizeIndexNameWithVersion(index));
    try {
      return prefixAwareClient.exists(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String message = String.format(
        "Could not check if [%s] index exists.",index.getIndexName()
      );
      throw new OptimizeRuntimeException(message, e);
    }
  }

}
