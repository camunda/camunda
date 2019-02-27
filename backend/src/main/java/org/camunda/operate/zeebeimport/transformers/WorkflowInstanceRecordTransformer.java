/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.transformers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.OperateZeebeEntity;
import org.camunda.operate.util.IdUtil;
import org.camunda.operate.zeebe.payload.PayloadUtil;
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
import org.camunda.operate.zeebeimport.record.value.WorkflowInstanceRecordValueImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.exporter.record.Record;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.PAYLOAD_UPDATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN;

@Component
public class WorkflowInstanceRecordTransformer implements AbstractRecordTransformer {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowInstanceRecordTransformer.class);

  //these event states end up in changes in workflow instance entity
  private static final Set<String> STATES_TO_LOAD = new HashSet<>();
  private static final String START_STATES;
  private static final Set<String> FINISH_STATES = new HashSet<>();

  static {
    START_STATES = ELEMENT_ACTIVATED.name();

    FINISH_STATES.add(ELEMENT_COMPLETED.name());
    FINISH_STATES.add(ELEMENT_TERMINATED.name());

    //    STATES_TO_LOAD.add(PAYLOAD_UPDATED);        //to record changed payload

    STATES_TO_LOAD.add(START_STATES);
    STATES_TO_LOAD.addAll(FINISH_STATES);
    STATES_TO_LOAD.add(PAYLOAD_UPDATED.name());
  }

  @Autowired
  private PayloadUtil payloadUtil;

  @Autowired
  private WorkflowCache workflowCache;

  @Override
  public List<OperateZeebeEntity> convert(Record record) {

//TODO    ZeebeUtil.ALL_EVENTS_LOGGER.debug(record.toJson());

    List<OperateZeebeEntity> entitiesToPersist = new ArrayList<>();

    final String intentStr = record.getMetadata().getIntent().name();

    if (STATES_TO_LOAD.contains(intentStr)) {
      final OperateZeebeEntity event = convertEvent(record);
      if (event != null) {
        entitiesToPersist.add(event);
      }
    }

    return entitiesToPersist;

  }

  private OperateZeebeEntity convertEvent(Record record) {
    final String intentStr = record.getMetadata().getIntent().name();

    //we will store sequence flows separately, no need to store them in events
    if (!intentStr.equals(SEQUENCE_FLOW_TAKEN.name())) {

      EventEntity eventEntity = new EventEntity();

      loadEventGeneralData(record, eventEntity);

      WorkflowInstanceRecordValueImpl recordValue = (WorkflowInstanceRecordValueImpl)record.getValue();

      eventEntity.setWorkflowId(String.valueOf(recordValue.getWorkflowKey()));
      eventEntity.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
      eventEntity.setBpmnProcessId(recordValue.getBpmnProcessId());

      if (recordValue.getElementId() != null) {
        eventEntity.setActivityId(recordValue.getElementId());
      }

      if (record.getKey() != recordValue.getWorkflowInstanceKey()) {
        eventEntity.setActivityInstanceId(IdUtil.getId(record));
      }

      return eventEntity;
    }
    return null;
  }

}
