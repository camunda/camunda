/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.es.contract;

import org.elasticsearch.action.index.IndexRequest;

import java.time.OffsetDateTime;

public interface MetricContract {

  String EVENT_PROCESS_INSTANCE_FINISHED = "EVENT_PROCESS_INSTANCE_FINISHED";

  interface Reader {
    Long retrieveProcessInstanceCount(OffsetDateTime startTime, OffsetDateTime endTime);
  }

  interface Writer {
    IndexRequest registerProcessInstanceCompleteEvent(String processInstanceKey, OffsetDateTime timestamp);
  }
}
