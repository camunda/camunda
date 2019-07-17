/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.springframework.stereotype.Component;

@Component
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
      "for (def oldEvent : ctx._source.events) {" +
        "params.events.removeIf(item -> item.id.equals(oldEvent.id));" +
      "}" +
      "ctx._source.events.addAll(params.events)";
    // @formatter:on
  }

}