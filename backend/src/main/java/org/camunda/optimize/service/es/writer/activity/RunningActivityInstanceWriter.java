/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RunningActivityInstanceWriter extends AbstractActivityInstanceWriter {

  public RunningActivityInstanceWriter(final OptimizeElasticsearchClient esClient,
                                       final ObjectMapper objectMapper) {
    super(esClient, objectMapper);
  }

  protected String createInlineUpdateScript() {
    // already imported events should win over the
    // new instances, since the stored instances are
    // probably completed activity instances.
    // @formatter:off
    return
      "def existingEventsById = ctx._source.events.stream().collect(Collectors.toMap(e -> e.id, e -> e, (e1, e2) -> e1));" +
      "def eventsToAddById = params.events.stream()" +
        ".filter(e -> !existingEventsById.containsKey(e.id))" +
        ".collect(Collectors.toMap(e -> e.id, e -> e, (e1, e2) -> e1));" +
      "ctx._source.events.addAll(eventsToAddById.values());";
    // @formatter:on
  }

}