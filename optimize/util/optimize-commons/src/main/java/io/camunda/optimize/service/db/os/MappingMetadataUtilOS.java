/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os;

import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.os.schema.index.DecisionInstanceIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessInstanceIndexOS;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.MappingMetadataUtil;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Collection;
import org.opensearch.client.opensearch.indices.IndexSettings.Builder;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class MappingMetadataUtilOS extends MappingMetadataUtil<Builder> {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(MappingMetadataUtilOS.class);

  public MappingMetadataUtilOS(final OptimizeElasticsearchClient dbClient) {
    super(dbClient);
  }

  @Override
  protected DecisionInstanceIndexOS getDecisionInstanceIndex(final String key) {
    return new DecisionInstanceIndexOS(key);
  }

  @Override
  protected ProcessInstanceIndexOS getProcessInstanceIndex(final String key) {
    return new ProcessInstanceIndexOS(key);
  }

  @Override
  protected Collection<? extends IndexMappingCreator<Builder>> getAllNonDynamicMappings() {
    return OpenSearchSchemaManager.getAllNonDynamicMappings();
  }
}
