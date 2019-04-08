/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CompletedActivityInstanceWriter extends AbstractActivityInstanceWriter {

  @Autowired
  public CompletedActivityInstanceWriter(RestHighLevelClient esClient,
                                         ObjectMapper objectMapper) {
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