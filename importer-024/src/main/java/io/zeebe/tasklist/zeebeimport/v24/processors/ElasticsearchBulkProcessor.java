/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport.v24.processors;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.elasticsearch.action.bulk.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.exceptions.PersistenceException;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.zeebe.ImportValueType;
import io.zeebe.tasklist.zeebeimport.AbstractImportBatchProcessor;
import io.zeebe.tasklist.zeebeimport.ImportBatch;
import io.zeebe.tasklist.zeebeimport.v24.record.RecordImpl;
import io.zeebe.tasklist.zeebeimport.v24.record.value.JobRecordValueImpl;
import io.zeebe.tasklist.zeebeimport.v24.record.value.VariableDocumentRecordImpl;
import io.zeebe.tasklist.zeebeimport.v24.record.value.VariableRecordValueImpl;
import io.zeebe.tasklist.zeebeimport.v24.record.value.WorkflowInstanceRecordValueImpl;

@Component
public class ElasticsearchBulkProcessor extends AbstractImportBatchProcessor {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchBulkProcessor.class);

  @Autowired
  private ActivityInstanceZeebeRecordProcessor activityInstanceZeebeRecordProcessor;

  @Autowired
  private VariableZeebeRecordProcessor variableZeebeRecordProcessor;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  protected void processZeebeRecords(ImportBatch importBatch, BulkRequest bulkRequest) throws PersistenceException {

    JavaType valueType = objectMapper.getTypeFactory().constructParametricType(RecordImpl.class, getRecordValueClass(importBatch.getImportValueType()));
    final List<Record> zeebeRecords = ElasticsearchUtil.mapSearchHits(importBatch.getHits(), objectMapper, valueType);

    ImportValueType importValueType = importBatch.getImportValueType();

    logger.debug("Writing [{}] Zeebe records to Elasticsearch", zeebeRecords.size());

    switch (importValueType) {
    case WORKFLOW_INSTANCE:
      Map<Long, List<RecordImpl<WorkflowInstanceRecordValueImpl>>> groupedWIRecordsPerActivityInst = zeebeRecords.stream()
          .map(obj -> (RecordImpl<WorkflowInstanceRecordValueImpl>) obj).collect(Collectors.groupingBy(obj -> obj.getKey()));
      activityInstanceZeebeRecordProcessor.processWorkflowInstanceRecord(groupedWIRecordsPerActivityInst, bulkRequest);
      break;
    case VARIABLE:
      // old style
      for (Record record : zeebeRecords) {
        variableZeebeRecordProcessor.processVariableRecord(record, bulkRequest);
      }
      break;
    case JOB:
      // TODO
      break;
    default:
      logger.debug("Default case triggered for type {}", importValueType);
      break;
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
    default:
      throw new TasklistRuntimeException(String.format("No value type class found for: %s", importValueType));
    }
  }

  @Override
  public String getZeebeVersion() {
    return "0.24";
  }
}
