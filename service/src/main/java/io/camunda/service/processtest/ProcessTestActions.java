/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.processtest;

import io.camunda.zeebe.engine.inmemory.CommandRecord;
import io.camunda.zeebe.engine.inmemory.EventPosition;
import io.camunda.zeebe.engine.inmemory.InMemoryEngine;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import java.time.Duration;
import java.util.List;

public class ProcessTestActions {

  private final InMemoryEngine engine;
  private final Duration idleTimeout;

  public ProcessTestActions(final InMemoryEngine engine, final Duration idleTimeout) {
    this.engine = engine;
    this.idleTimeout = idleTimeout;
  }

  public long deployProcess(final String name, final String bpmnXml) {
    final CommandRecord command = ProcessTestCommands.deployResources(name, bpmnXml);
    final EventPosition eventPosition = engine.writeCommand(command);

    engine.waitForIdleState(idleTimeout);

    final DeploymentRecordValue createdEvent =
        getFollowUpRecords(eventPosition).stream()
            .filter(record -> record.getIntent() == DeploymentIntent.CREATED)
            .findFirst()
            .map(record -> (DeploymentRecordValue) record.getValue())
            .orElseThrow();

    return createdEvent.getDeploymentKey();
  }

  public long createProcessInstance(final String processId, final String variables) {

    final CommandRecord command = ProcessTestCommands.createProcessInstance(processId, variables);
    final EventPosition eventPosition = engine.writeCommand(command);

    engine.waitForIdleState(idleTimeout);

    final ProcessInstanceCreationRecordValue createdEvent =
        getFollowUpRecords(eventPosition).stream()
            .filter(record -> record.getIntent() == ProcessInstanceCreationIntent.CREATED)
            .findFirst()
            .map(record -> (ProcessInstanceCreationRecordValue) record.getValue())
            .orElseThrow();

    return createdEvent.getProcessInstanceKey();
  }

  public long completeJob(final String jobType, final String variables) {
    engine.waitForIdleState(idleTimeout);

    final Long jobKey =
        engine.getRecordStreamView().getRecords().stream()
            .filter(record -> record.getValueType() == ValueType.JOB)
            .filter(record -> ((JobRecordValue) record.getValue()).getType().equals(jobType))
            .map(Record::getKey)
            .toList()
            .getLast();

    final CommandRecord command = ProcessTestCommands.completeJob(variables);
    final EventPosition eventPosition = engine.writeCommand(command, jobKey);

    engine.waitForIdleState(idleTimeout);

    getFollowUpRecords(eventPosition).stream()
        .filter(record -> record.getIntent() == JobIntent.COMPLETED)
        .findFirst()
        .orElseThrow();

    return jobKey;
  }

  private List<Record<?>> getFollowUpRecords(final EventPosition eventPosition) {
    return engine.getRecordStreamView().getRecords().stream()
        .filter(record -> record.getSourceRecordPosition() == eventPosition.position())
        .toList();
  }
}
