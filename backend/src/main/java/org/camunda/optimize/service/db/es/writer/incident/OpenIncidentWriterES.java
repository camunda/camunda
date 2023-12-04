/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.writer.incident.OpenIncidentWriter;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class OpenIncidentWriterES extends AbstractIncidentWriterES implements OpenIncidentWriter {

  public OpenIncidentWriterES(final OptimizeElasticsearchClient esClient,
                              final ElasticSearchSchemaManager elasticSearchSchemaManager,
                              final ObjectMapper objectMapper) {
    super(esClient, elasticSearchSchemaManager, objectMapper);
  }

  @Override
  protected String createInlineUpdateScript() {
    // already imported incidents should win over the
    // new instances, since the stored instances are
    // probably completed incidents.
    // @formatter:off
    return
      "def existingIncidentsById = ctx._source.incidents.stream().collect(Collectors.toMap(e -> e.id, e -> e, (e1, e2) -> e1));" +
      "def incidentsToAddById = params.incidents.stream()" +
        ".filter(e -> !existingIncidentsById.containsKey(e.id))" +
        ".collect(Collectors.toMap(e -> e.id, e -> e, (e1, e2) -> e1));" +
      "ctx._source.incidents.addAll(incidentsToAddById.values());";
    // @formatter:on
  }

}