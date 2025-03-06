/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.processors;

import static io.camunda.operate.schema.templates.JobTemplate.*;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;

import io.camunda.operate.entities.*;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.JobTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JobZeebeRecordProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobZeebeRecordProcessor.class);

  private static final Set<String> JOB_EVENTS = new HashSet<>();
  private static final Set<String> FAILED_JOB_EVENTS = new HashSet<>();

  static {
    JOB_EVENTS.add(JobIntent.CREATED.name());
    JOB_EVENTS.add(JobIntent.COMPLETED.name());
    JOB_EVENTS.add(JobIntent.TIMED_OUT.name());
    JOB_EVENTS.add(JobIntent.FAILED.name());
    JOB_EVENTS.add(JobIntent.RETRIES_UPDATED.name());
    JOB_EVENTS.add(JobIntent.CANCELED.name());
    JOB_EVENTS.add(JobIntent.ERROR_THROWN.name());
    JOB_EVENTS.add(JobIntent.MIGRATED.name());

    FAILED_JOB_EVENTS.add(JobIntent.FAILED.name());
    FAILED_JOB_EVENTS.add(JobIntent.ERROR_THROWN.name());
  }

  @Autowired private JobTemplate jobTemplate;

  public void processJobRecords(
      final Map<Long, List<Record<JobRecordValue>>> records, final BatchRequest batchRequest)
      throws PersistenceException {
    LOGGER.debug("Importing Job records.");
    for (final List<Record<JobRecordValue>> flowNodeJobRecords : records.values()) {
      final Map<Long, List<Record<JobRecordValue>>> groupedByJobKey =
          flowNodeJobRecords.stream()
              .collect(Collectors.groupingBy(jobRecord -> jobRecord.getKey()));
      for (final List<Record<JobRecordValue>> jobRecords : groupedByJobKey.values()) {
        processLastRecord(
            jobRecords,
            JOB_EVENTS,
            rethrowConsumer(
                record -> {
                  final JobRecordValue recordValue = (JobRecordValue) record.getValue();
                  processJob(record, recordValue, batchRequest);
                }));
      }
    }
  }

  private <T extends RecordValue> void processLastRecord(
      final List<Record<JobRecordValue>> records,
      final Set<String> events,
      final Consumer<Record<? extends RecordValue>> recordProcessor) {
    if (records.size() >= 1) {
      for (int i = records.size() - 1; i >= 0; i--) {
        final String intentStr = records.get(i).getIntent().name();
        if (events.contains(intentStr)) {
          if (i > 0 && FAILED_JOB_EVENTS.contains(intentStr)) {
            recordProcessor.accept(records.get(i - 1));
          }
          recordProcessor.accept(records.get(i));
          break;
        }
      }
    }
  }

  private void processJob(
      final Record record, final JobRecordValue recordValue, final BatchRequest batchRequest)
      throws PersistenceException {
    final JobEntity jobEntity =
        new JobEntity()
            .setId(Long.toString(record.getKey()))
            .setKey(record.getKey())
            .setPartitionId(record.getPartitionId())
            .setProcessInstanceKey(recordValue.getProcessInstanceKey())
            .setFlowNodeInstanceId(recordValue.getElementInstanceKey())
            .setTenantId(recordValue.getTenantId())
            .setType(recordValue.getType())
            .setWorker(recordValue.getWorker())
            .setState(record.getIntent().name())
            .setRetries(recordValue.getRetries())
            .setErrorMessage(recordValue.getErrorMessage())
            .setErrorCode(recordValue.getErrorCode())
            .setEndTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
            .setCustomHeaders(recordValue.getCustomHeaders())
            .setJobKind(recordValue.getJobKind().name())
            .setFlowNodeId(recordValue.getElementId());

    if (recordValue.getJobListenerEventType() != null) {
      jobEntity.setListenerEventType(recordValue.getJobListenerEventType().name());
    }
    final long jobDeadline = recordValue.getDeadline();
    if (jobDeadline >= 0) {
      jobEntity.setDeadline(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(jobDeadline)));
    }

    if (FAILED_JOB_EVENTS.contains(record.getIntent().name())) {
      // set flowNodeId to null to not overwrite it (because zeebe puts an error message there)
      jobEntity.setFlowNodeId(null);
      if (recordValue.getRetries() > 0) {
        jobEntity.setJobFailedWithRetriesLeft(true);
      } else {
        jobEntity.setJobFailedWithRetriesLeft(false);
      }
    }
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(FLOW_NODE_ID, jobEntity.getFlowNodeId());
    updateFields.put(JOB_WORKER, jobEntity.getWorker());
    updateFields.put(JOB_STATE, jobEntity.getState());
    updateFields.put(RETRIES, jobEntity.getRetries());
    updateFields.put(ERROR_MESSAGE, jobEntity.getErrorMessage());
    updateFields.put(ERROR_CODE, jobEntity.getErrorCode());
    updateFields.put(TIME, jobEntity.getEndTime());
    updateFields.put(CUSTOM_HEADERS, jobEntity.getCustomHeaders());
    updateFields.put(JOB_DEADLINE, jobEntity.getDeadline());

    batchRequest.upsert(
        jobTemplate.getFullQualifiedName(), jobEntity.getId(), jobEntity, updateFields);
  }
}
