/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.query;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.ZeebeDbState;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class StateQueryService implements QueryService {
  private final ProcessState processes;
  private final ElementInstanceState instances;
  private final JobState jobs;

  private volatile boolean isClosed;

  public StateQueryService(final ZeebeDb<ZbColumnFamilies> zeebeDb) {
    final ZeebeState state = new ZeebeDbState(zeebeDb, zeebeDb.createContext());
    processes = state.getProcessState();
    instances = state.getElementInstanceState();
    jobs = state.getJobState();
  }

  @Override
  public void close() {
    isClosed = true;
  }

  @Override
  public Optional<DirectBuffer> getBpmnProcessIdForProcess(final long key) {
    ensureServiceIsOpened();

    return Optional.ofNullable(processes.getProcessByKey(key))
        .map(DeployedProcess::getBpmnProcessId);
  }

  @Override
  public Optional<DirectBuffer> getBpmnProcessIdForProcessInstance(final long key) {
    ensureServiceIsOpened();

    return Optional.ofNullable(instances.getInstance(key))
        .map(ElementInstance::getValue)
        .map(ProcessInstanceRecord::getBpmnProcessIdBuffer);
  }

  @Override
  public Optional<DirectBuffer> getBpmnProcessIdForJob(final long key) {
    ensureServiceIsOpened();

    return Optional.ofNullable(jobs.getJob(key)).map(JobRecord::getBpmnProcessIdBuffer);
  }

  private void ensureServiceIsOpened() {
    if (isClosed) {
      throw new ClosedServiceException();
    }
  }
}
