/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.v8_7.processors.processors;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.entities.SequenceFlowEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SequenceFlowZeebeRecordProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SequenceFlowZeebeRecordProcessor.class);
  private static final String ID_PATTERN = "%s_%s";

  @Autowired private SequenceFlowTemplate sequenceFlowTemplate;

  public void processSequenceFlowRecord(final Record record, final BatchRequest batchRequest)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();
    if (intentStr.equals(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN.name())) {
      final ProcessInstanceRecordValue recordValue = (ProcessInstanceRecordValue) record.getValue();
      persistSequenceFlow(record, recordValue, batchRequest);
    }
  }

  private void persistSequenceFlow(
      final Record record,
      final ProcessInstanceRecordValue recordValue,
      final BatchRequest batchRequest)
      throws PersistenceException {
    final SequenceFlowEntity entity =
        new SequenceFlowEntity()
            .setId(
                String.format(
                    ID_PATTERN, recordValue.getProcessInstanceKey(), recordValue.getElementId()))
            .setProcessInstanceKey(recordValue.getProcessInstanceKey())
            .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
            .setBpmnProcessId(recordValue.getBpmnProcessId())
            .setActivityId(recordValue.getElementId())
            .setTenantId(tenantOrDefault(recordValue.getTenantId()));

    LOGGER.debug("Index sequence flow: id {}", entity.getId());
    batchRequest.add(sequenceFlowTemplate.getFullQualifiedName(), entity);
  }
}
