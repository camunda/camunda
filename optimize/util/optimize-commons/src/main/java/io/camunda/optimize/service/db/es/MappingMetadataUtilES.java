/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es;

import co.elastic.clients.elasticsearch.indices.IndexSettings;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.es.schema.index.DecisionInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.MappingMetadataUtil;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Collection;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class MappingMetadataUtilES extends MappingMetadataUtil<IndexSettings.Builder> {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(MappingMetadataUtilES.class);

  @Override
  protected DecisionInstanceIndexES getDecisionInstanceIndex(final String key) {
    return new DecisionInstanceIndexES(key);
  }

  @Override
  protected ProcessInstanceIndexES getProcessInstanceIndex(final String key) {
    return new ProcessInstanceIndexES(key);
  }

  @Override
  protected Collection<? extends IndexMappingCreator<IndexSettings.Builder>>
      getAllNonDynamicMappings() {
    return ElasticSearchSchemaManager.getAllNonDynamicMappings();
  }
}
