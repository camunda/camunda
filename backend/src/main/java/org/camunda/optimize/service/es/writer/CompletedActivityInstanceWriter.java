/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CompletedActivityInstanceWriter extends AbstractActivityInstanceWriter {

  public CompletedActivityInstanceWriter(final OptimizeElasticsearchClient esClient,
                                         final ObjectMapper objectMapper) {
    super(esClient, objectMapper);
  }

  protected String createInlineUpdateScript() {
    // new import events should win over already
    // import events, since those might be running
    // activity instances.
    // @formatter:off
    return
      "for (def newEvent : params.events) {" +
        "ctx._source.events.removeIf(item -> item.id.equals(newEvent.id)) ;" +
      "}" +
      "ctx._source.events.addAll(params.events)";
    // @formatter:on
  }

}