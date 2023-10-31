/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_4.processors;

import io.camunda.operate.entities.SequenceFlowEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

@Component
public class SequenceFlowZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(SequenceFlowZeebeRecordProcessor.class);
  private static final String ID_PATTERN = "%s_%s";

  @Autowired
  private SequenceFlowTemplate sequenceFlowTemplate;

  public void processSequenceFlowRecord(Record record, BatchRequest batchRequest) throws PersistenceException {
    final String intentStr = record.getIntent().name();
    if (intentStr.equals(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN.name())) {
      ProcessInstanceRecordValue recordValue = (ProcessInstanceRecordValue)record.getValue();
      persistSequenceFlow(record, recordValue, batchRequest);
    }
  }

  private void persistSequenceFlow(Record record, ProcessInstanceRecordValue recordValue, BatchRequest batchRequest) throws PersistenceException {
    SequenceFlowEntity entity = new SequenceFlowEntity()
        .setId(String.format(ID_PATTERN, recordValue.getProcessInstanceKey(), recordValue.getElementId()))
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setActivityId(recordValue.getElementId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));

    logger.debug("Index sequence flow: id {}", entity.getId());
    batchRequest.add(sequenceFlowTemplate.getFullQualifiedName(), entity);
  }

}
