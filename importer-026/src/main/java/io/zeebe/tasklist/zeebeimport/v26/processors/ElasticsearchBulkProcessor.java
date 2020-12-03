/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport.v26.processors;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.tasklist.exceptions.PersistenceException;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.zeebe.ImportValueType;
import io.zeebe.tasklist.zeebeimport.AbstractImportBatchProcessor;
import io.zeebe.tasklist.zeebeimport.ImportBatch;
import io.zeebe.tasklist.zeebeimport.v26.record.RecordImpl;
import io.zeebe.tasklist.zeebeimport.v26.record.value.DeploymentRecordValueImpl;
import io.zeebe.tasklist.zeebeimport.v26.record.value.JobRecordValueImpl;
import io.zeebe.tasklist.zeebeimport.v26.record.value.VariableDocumentRecordImpl;
import io.zeebe.tasklist.zeebeimport.v26.record.value.VariableRecordValueImpl;
import io.zeebe.tasklist.zeebeimport.v26.record.value.WorkflowInstanceRecordValueImpl;
import java.util.List;
import org.elasticsearch.action.bulk.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchBulkProcessor extends AbstractImportBatchProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchBulkProcessor.class);

  @Autowired private WorkflowInstanceZeebeRecordProcessor workflowInstanceZeebeRecordProcessor;

  @Autowired private VariableZeebeRecordProcessor variableZeebeRecordProcessor;

  @Autowired private JobZeebeRecordProcessor jobZeebeRecordProcessor;

  @Autowired private WorkflowZeebeRecordProcessor workflowZeebeRecordProcessor;

  @Autowired private ObjectMapper objectMapper;

  @Override
  protected void processZeebeRecords(ImportBatch importBatch, BulkRequest bulkRequest)
      throws PersistenceException {

    final JavaType valueType =
        objectMapper
            .getTypeFactory()
            .constructParametricType(
                RecordImpl.class, getRecordValueClass(importBatch.getImportValueType()));
    final List<Record> zeebeRecords =
        ElasticsearchUtil.mapSearchHits(importBatch.getHits(), objectMapper, valueType);

    final ImportValueType importValueType = importBatch.getImportValueType();

    LOGGER.debug("Writing [{}] Zeebe records to Elasticsearch", zeebeRecords.size());
    for (Record record : zeebeRecords) {
      switch (importValueType) {
        case WORKFLOW_INSTANCE:
          workflowInstanceZeebeRecordProcessor.processWorkflowInstanceRecord(record, bulkRequest);
          break;
        case VARIABLE:
          variableZeebeRecordProcessor.processVariableRecord(record, bulkRequest);
          break;
        case JOB:
          jobZeebeRecordProcessor.processJobRecord(record, bulkRequest);
          break;
        case DEPLOYMENT:
          // deployment records can be processed one by one
          workflowZeebeRecordProcessor.processDeploymentRecord(record, bulkRequest);
          break;
        default:
          LOGGER.debug("Default case triggered for type {}", importValueType);
          break;
      }
    }
  }

  protected Class<? extends RecordValue> getRecordValueClass(ImportValueType importValueType) {
    switch (importValueType) {
      case WORKFLOW_INSTANCE:
        return WorkflowInstanceRecordValueImpl.class;
      case JOB:
        return JobRecordValueImpl.class;
      case VARIABLE:
        return VariableRecordValueImpl.class;
      case VARIABLE_DOCUMENT:
        return VariableDocumentRecordImpl.class;
      case DEPLOYMENT:
        return DeploymentRecordValueImpl.class;
      default:
        throw new TasklistRuntimeException(
            String.format("No value type class found for: %s", importValueType));
    }
  }

  @Override
  public String getZeebeVersion() {
    return "0.26";
  }
}
