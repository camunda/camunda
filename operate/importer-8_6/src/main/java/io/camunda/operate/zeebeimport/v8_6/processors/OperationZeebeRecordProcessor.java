/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.v8_6.processors;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.OperationsManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OperationZeebeRecordProcessor {

  private static final Set<String> VARIABLE_DOCUMENT_STATES = new HashSet<>();

  static {
    VARIABLE_DOCUMENT_STATES.add(VariableDocumentIntent.UPDATED.name());
  }

  @Autowired private OperationsManager operationsManager;

  public void processVariableDocumentRecords(final Record record, final BatchRequest batchRequest)
      throws PersistenceException {
    if (!VARIABLE_DOCUMENT_STATES.contains(record.getIntent().name())) {
      return;
    }
    operationsManager.completeOperation(record.getKey(), null, null, null, batchRequest);
  }
}
