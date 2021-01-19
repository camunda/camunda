/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.v27.processors;

import java.util.HashSet;
import java.util.Set;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.schema.templates.OperationTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.zeebeimport.ElasticsearchManager;
import org.camunda.operate.zeebeimport.v27.record.Intent;
import org.elasticsearch.action.bulk.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.protocol.record.Record;

@Component
public class OperationZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(VariableZeebeRecordProcessor.class);

  private static final Set<String> VARIABLE_DOCUMENT_STATES = new HashSet<>();

  static {
    VARIABLE_DOCUMENT_STATES.add(Intent.UPDATED.name());
  }

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private ElasticsearchManager elasticsearchManager;

  public void processVariableDocumentRecords(Record record, BulkRequest bulkRequest) throws PersistenceException {
    if (!VARIABLE_DOCUMENT_STATES.contains(record.getIntent().name())) {
      return;
    }
    elasticsearchManager.completeOperation(record.getKey(), null, null, OperationType.UPDATE_VARIABLE, bulkRequest);
  }
}
