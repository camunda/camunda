/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.processors.es;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.ImportBatch;
import io.camunda.tasklist.zeebeimport.es.AbstractImportBatchProcessorElasticSearch;
import io.camunda.tasklist.zeebeimport.v870.record.RecordImpl;
import io.camunda.tasklist.zeebeimport.v870.record.value.JobRecordValueImpl;
import io.camunda.tasklist.zeebeimport.v870.record.value.ProcessInstanceRecordValueImpl;
import io.camunda.tasklist.zeebeimport.v870.record.value.UserTaskRecordValueImpl;
import io.camunda.tasklist.zeebeimport.v870.record.value.VariableRecordValueImpl;
import io.camunda.tasklist.zeebeimport.v870.record.value.deployment.DeployedProcessImpl;
import io.camunda.tasklist.zeebeimport.v870.record.value.deployment.FormRecordImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.elasticsearch.action.bulk.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class BulkProcessorElasticSearch extends AbstractImportBatchProcessorElasticSearch {

  private static final Logger LOGGER = LoggerFactory.getLogger(BulkProcessorElasticSearch.class);

  @Autowired
  private ProcessInstanceZeebeRecordProcessorElasticSearch processInstanceZeebeRecordProcessor;

  @Autowired private VariableZeebeRecordProcessorElasticSearch variableZeebeRecordProcessor;

  @Autowired private JobZeebeRecordProcessorElasticSearch jobZeebeRecordProcessor;

  @Autowired private ProcessZeebeRecordProcessorElasticSearch processZeebeRecordProcessor;

  @Autowired private FormZeebeRecordProcessorElasticSearch formZeebeRecordProcessor;

  @Autowired private UserTaskZeebeRecordProcessorElasticSearch userTaskZeebeRecordProcessor;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private Metrics metrics;

  @Override
  protected void processZeebeRecords(
      final ImportBatch importBatchElasticSearch, final BulkRequest bulkRequest)
      throws PersistenceException {

    final JavaType valueType =
        objectMapper
            .getTypeFactory()
            .constructParametricType(
                RecordImpl.class,
                getRecordValueClass(importBatchElasticSearch.getImportValueType()));
    final List<Record> zeebeRecords =
        ElasticsearchUtil.mapSearchHits(
            importBatchElasticSearch.getHits(), objectMapper, valueType);

    final ImportValueType importValueType = importBatchElasticSearch.getImportValueType();

    LOGGER.debug("Writing [{}] Zeebe records to Elasticsearch", zeebeRecords.size());

    for (final Record record : zeebeRecords) {
      switch (importValueType) {
        case PROCESS_INSTANCE:
          processInstanceZeebeRecordProcessor.processProcessInstanceRecord(record, bulkRequest);
          break;
        case VARIABLE:
          variableZeebeRecordProcessor.processVariableRecord(record, bulkRequest);
          break;
        case JOB:
          jobZeebeRecordProcessor.processJobRecord(record, bulkRequest);
          break;
        case PROCESS:
          // deployment records can be processed one by one
          processZeebeRecordProcessor.processDeploymentRecord(record, bulkRequest);
          break;
        case FORM:
          formZeebeRecordProcessor.processFormRecord(record, bulkRequest);
          break;
        case USER_TASK:
          userTaskZeebeRecordProcessor.processUserTaskRecord(record, bulkRequest);
          break;
        default:
          LOGGER.debug("Default case triggered for type {}", importValueType);
          break;
      }
    }
    recordRecordImportTime(zeebeRecords);
  }

  private void recordRecordImportTime(final List<Record> zeebeRecords) {
    final var currentTime = OffsetDateTime.now().toInstant().toEpochMilli();
    zeebeRecords.forEach(
        record ->
            metrics
                .getTimer(
                    Metrics.TIMER_NAME_IMPORT_TIME,
                    Metrics.TAG_KEY_TYPE,
                    record.getValueType().toString(),
                    Metrics.TAG_KEY_PARTITION,
                    String.valueOf(record.getPartitionId()))
                .record(currentTime - record.getTimestamp(), TimeUnit.MILLISECONDS));
  }

  protected Class<? extends RecordValue> getRecordValueClass(
      final ImportValueType importValueType) {
    switch (importValueType) {
      case PROCESS_INSTANCE:
        return ProcessInstanceRecordValueImpl.class;
      case JOB:
        return JobRecordValueImpl.class;
      case VARIABLE:
        return VariableRecordValueImpl.class;
      case PROCESS:
        return DeployedProcessImpl.class;
      case FORM:
        return FormRecordImpl.class;
      case USER_TASK:
        return UserTaskRecordValueImpl.class;
      default:
        throw new TasklistRuntimeException(
            String.format("No value type class found for: %s", importValueType));
    }
  }

  @Override
  public String getZeebeVersion() {
    return "8.7";
  }
}
