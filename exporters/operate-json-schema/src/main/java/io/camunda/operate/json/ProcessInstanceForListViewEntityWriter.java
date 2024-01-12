/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.operate.json;

import com.fasterxml.jackson.core.JsonGenerator;
import io.camunda.operate.entities.listview.ListViewJoinRelation;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ProcessInstanceForListViewEntityWriter
    implements OperateEntityWriter<ProcessInstanceForListViewEntity> {

  private static final byte[] PROCESS_INSTANCE_KEY =
      "processInstanceKey".getBytes(StandardCharsets.UTF_8);

  @Override
  public void writeTo(ProcessInstanceForListViewEntity entity, JsonGenerator jsonGenerator)
      throws IOException {

    // TODO: need to be aware which fields can be nullable

    jsonGenerator.writeStartObject();

    jsonGenerator.writeStringField("id", entity.getId());

    jsonGenerator.writeFieldName("key");
    jsonGenerator.writeNumber(entity.getKey());

    jsonGenerator.writeFieldName("partitionId");
    jsonGenerator.writeNumber(entity.getPartitionId());

    jsonGenerator.writeFieldName("processInstanceKey");
    jsonGenerator.writeNumber(entity.getProcessInstanceKey());

    jsonGenerator.writeFieldName("processDefinitionKey");
    jsonGenerator.writeNumber(entity.getProcessDefinitionKey());

    jsonGenerator.writeFieldName("processName");
    jsonGenerator.writeString(entity.getProcessName());

    jsonGenerator.writeFieldName("processVersion");
    jsonGenerator.writeNumber(entity.getProcessVersion());

    jsonGenerator.writeFieldName("bpmnProcessId");
    jsonGenerator.writeString(entity.getBpmnProcessId());

    jsonGenerator.writeFieldName("startDate");
    // TODO: can be optimized to write a string directly
    jsonGenerator.writePOJO(entity.getStartDate());
    // TODO date

    jsonGenerator.writeFieldName("endDate");
    // TODO: can be optimized to write a string directly
    jsonGenerator.writePOJO(entity.getEndDate());

    jsonGenerator.writeFieldName("state");
    jsonGenerator.writeString(entity.getState().toString());

    jsonGenerator.writeFieldName("batchOperationIds");
    jsonGenerator.writeStartArray();

    List<String> batchOperationIds = entity.getBatchOperationIds();
    for (int i = 0; i < batchOperationIds.size(); i++) {
      jsonGenerator.writeString(batchOperationIds.get(i));
    }

    jsonGenerator.writeEndArray();

    jsonGenerator.writeFieldName("parentProcessInstanceKey");
    jsonGenerator.writeNumber(entity.getParentProcessInstanceKey());

    jsonGenerator.writeFieldName("parentFlowNodeInstanceKey");
    jsonGenerator.writeNumber(entity.getParentFlowNodeInstanceKey());

    jsonGenerator.writeFieldName("treePath");
    jsonGenerator.writeString(entity.getTreePath());

    jsonGenerator.writeFieldName("incident");
    jsonGenerator.writeBoolean(entity.isIncident());

    jsonGenerator.writeFieldName("tenantId");
    jsonGenerator.writeString(entity.getTenantId());

    jsonGenerator.writeFieldName("joinRelation");
    ListViewJoinRelation joinRelation = entity.getJoinRelation();

    if (joinRelation != null) {
      jsonGenerator.writeStartObject();

      jsonGenerator.writeStringField("name", joinRelation.getName());
      jsonGenerator.writeNumberField("parent", joinRelation.getParent());

      jsonGenerator.writeEndObject();
    } else {
      jsonGenerator.writeNull();
    }

    // plus fields in super class

    jsonGenerator.writeEndObject();
  }
}
