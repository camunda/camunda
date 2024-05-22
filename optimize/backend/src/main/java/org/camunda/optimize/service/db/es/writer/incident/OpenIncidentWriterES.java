/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.db.writer.incident.OpenIncidentWriter;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class OpenIncidentWriterES extends AbstractIncidentWriterES implements OpenIncidentWriter {

  public OpenIncidentWriterES(
      final OptimizeElasticsearchClient esClient,
      final ElasticSearchSchemaManager elasticSearchSchemaManager,
      final ObjectMapper objectMapper) {
    super(esClient, elasticSearchSchemaManager, objectMapper);
  }
}
