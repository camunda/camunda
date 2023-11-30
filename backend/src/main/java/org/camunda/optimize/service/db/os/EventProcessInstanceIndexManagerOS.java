/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.EventProcessInstanceIndexManager;
import org.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.db.reader.EventProcessPublishStateReader;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Conditional(OpenSearchCondition.class)
public class EventProcessInstanceIndexManagerOS extends EventProcessInstanceIndexManager {

  private final OptimizeOpenSearchClient optimizeOpenSearchClient;
  private final OpenSearchSchemaManager openSearchSchemaManager;

  public EventProcessInstanceIndexManagerOS(final OptimizeOpenSearchClient optimizeOpenSearchClient,
                                            final OpenSearchSchemaManager openSearchSchemaManager,
                                            final EventProcessPublishStateReader eventProcessPublishStateReader,
                                            final OptimizeIndexNameService indexNameService) {
    super(eventProcessPublishStateReader, indexNameService);
    this.optimizeOpenSearchClient = optimizeOpenSearchClient;
    this.openSearchSchemaManager = openSearchSchemaManager;
  }

  @Override
  public void syncAvailableIndices() {
    //todo will be handled in the OPT-7376
  }

}
