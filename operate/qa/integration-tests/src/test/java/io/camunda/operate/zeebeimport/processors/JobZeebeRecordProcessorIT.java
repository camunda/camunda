/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.processors;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.operate.entities.ListenerState;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.JobTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.reader.ListenerReader;
import io.camunda.operate.webapp.rest.dto.ListenerDto;
import io.camunda.operate.webapp.rest.dto.ListenerRequestDto;
import io.camunda.operate.webapp.rest.dto.ListenerResponseDto;
import io.camunda.operate.webapp.rest.dto.SortingDto;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class JobZeebeRecordProcessorIT extends OperateSearchAbstractIT {

  @Autowired ListenerReader listenerReader;
  @Autowired private JobZeebeRecordProcessor underTest;
  @Autowired private JobTemplate jobTemplate;
  @Autowired private BeanFactory beanFactory;

  @Test
  public void shouldUpdateRecordsCorrectly() throws PersistenceException {
    final String elementId = "ExampleJobActivity";
    final long processInstanceId = 11113L;

    final long taskStartDate = 1724755457190L;
    final long taskCompletedDate = taskStartDate + 50;
    final Record<JobRecordValue> originalRecord =
        (Record<JobRecordValue>)
            createSimpleZeebeJobRecord(
                11111L,
                taskStartDate,
                JobIntent.CREATED,
                "job-processor-it",
                elementId,
                11112,
                JobKind.BPMN_ELEMENT,
                null,
                processInstanceId,
                "JobActivity");
    importJobZeebeRecords(List.of(originalRecord));

    final ListenerRequestDto listenerRequestDto =
        new ListenerRequestDto()
            .setPageSize(10)
            .setFlowNodeId(elementId)
            .setSorting(new SortingDto().setSortBy(JobTemplate.TIME).setSortOrder("desc"));
    final ListenerResponseDto response =
        listenerReader.getListenerExecutions(String.valueOf(processInstanceId), listenerRequestDto);

    // there is no general job reader at the moment so we can only check for listeners
    assertTrue(response.getTotalCount() == 0);

    final Record<JobRecordValue> updateRecord =
        createUpdatedZeebeJobRecordFrom(
            originalRecord,
            taskCompletedDate,
            JobIntent.COMPLETED,
            originalRecord.getValue().getElementId());
    final long listenerStartTime = taskStartDate + 60;
    final long listenerFailedTime = taskStartDate + 80;
    final Record<JobRecordValue> listenerRecord =
        createRecordForSameFlowNodeFrom(
            originalRecord,
            22222L,
            listenerStartTime,
            JobIntent.CREATED,
            elementId,
            JobKind.EXECUTION_LISTENER,
            JobListenerEventType.END,
            "ExampleListener");
    // different element ID to check if it does not get overwritten
    final Record<JobRecordValue> listenerErrorRecord =
        createUpdatedZeebeJobRecordFrom(
            listenerRecord, listenerFailedTime, JobIntent.ERROR_THROWN, "NO_CATCH_EVENT_FOUND");

    importJobZeebeRecords(List.of(updateRecord, listenerRecord, listenerErrorRecord));
    final ListenerResponseDto updateResponse =
        listenerReader.getListenerExecutions(String.valueOf(processInstanceId), listenerRequestDto);
    assertTrue(updateResponse.getTotalCount() == 1);
    final ListenerDto actualListenerRecord = updateResponse.getListeners().getFirst();
    assertTrue(actualListenerRecord.getJobType().equals("ExampleListener"));
    assertTrue(actualListenerRecord.getState().equals(ListenerState.FAILED));
  }

  private void importJobZeebeRecords(final List<Record<JobRecordValue>> zeebeRecords)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    underTest.processJobRecords(
        zeebeRecords.stream()
            .collect(Collectors.groupingBy(obj -> obj.getValue().getElementInstanceKey())),
        batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(jobTemplate.getFullQualifiedName());
  }

  private Record<JobRecordValue> createUpdatedZeebeJobRecordFrom(
      final Record<JobRecordValue> record,
      final long updateTime,
      final Intent updateIntent,
      final String updateElementId) {
    return createSimpleZeebeJobRecord(
        record.getKey(),
        updateTime,
        updateIntent,
        record.getValue().getBpmnProcessId(),
        updateElementId,
        record.getValue().getElementInstanceKey(),
        record.getValue().getJobKind(),
        record.getValue().getJobListenerEventType(),
        record.getValue().getProcessInstanceKey(),
        record.getValue().getType());
  }

  private Record<JobRecordValue> createRecordForSameFlowNodeFrom(
      final Record<JobRecordValue> record,
      final long key,
      final long time,
      final Intent intent,
      final String elementId,
      final JobKind jobKind,
      final JobListenerEventType listenerEventType,
      final String jobType) {
    return createSimpleZeebeJobRecord(
        key,
        time,
        intent,
        record.getValue().getBpmnProcessId(),
        elementId,
        record.getValue().getElementInstanceKey(),
        jobKind,
        listenerEventType,
        record.getValue().getProcessInstanceKey(),
        jobType);
  }

  private Record createSimpleZeebeJobRecord(
      final long key,
      final long time,
      final Intent intent,
      final String bpmnProcessId,
      final String elementId,
      final long elementInstanceKey,
      final JobKind jobKind,
      final JobListenerEventType listenerEventType,
      final long processInstanceKey,
      final String type) {
    final Map<String, Object> authorization =
        Map.of("authorized_tenants", List.of(IndexDescriptor.DEFAULT_TENANT_ID));
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .withBpmnProcessId(bpmnProcessId)
            .withElementId(elementId)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKind(jobKind)
            .withJobListenerEventType(listenerEventType)
            .withProcessDefinitionKey(1L)
            .withProcessDefinitionVersion(1)
            .withProcessInstanceKey(processInstanceKey)
            .withRetries(2)
            .withTenantId(IndexDescriptor.DEFAULT_TENANT_ID)
            .withType(type)
            .withDeadline(-1)
            .build();
    final Record<RecordValue> record =
        ImmutableRecord.builder()
            .withPosition(1)
            .withSourceRecordPosition(1L)
            .withKey(key)
            .withTimestamp(time)
            .withIntent(intent)
            .withPartitionId(1)
            .withRecordType(RecordType.EVENT)
            .withRejectionType(RejectionType.NULL_VAL)
            .withBrokerVersion("8.6.0")
            .withAuthorizations(authorization)
            .withRecordVersion(1)
            .withValueType(ValueType.JOB)
            .withValue(value)
            .withOperationReference(-1)
            .build();
    return record;
  }
}
