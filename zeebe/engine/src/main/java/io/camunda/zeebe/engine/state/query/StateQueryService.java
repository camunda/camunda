/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.query;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.InstantSource;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class StateQueryService implements QueryService {

  private volatile boolean isClosed;
  private ProcessingState state;
  private final ZeebeDb<ZbColumnFamilies> zeebeDb;
  private final InstantSource clock;

  public StateQueryService(final ZeebeDb<ZbColumnFamilies> zeebeDb, final InstantSource clock) {
    this.zeebeDb = zeebeDb;
    this.clock = clock;
  }

  @Override
  public void close() {
    isClosed = true;
  }

  @Override
  public Optional<DirectBuffer> getBpmnProcessIdForProcess(final long key) {
    ensureServiceIsOpened();

    return Optional.ofNullable(
            state
                .getProcessState()
                .getProcessByKeyAndTenant(key, TenantOwned.DEFAULT_TENANT_IDENTIFIER))
        .map(DeployedProcess::getBpmnProcessId);
  }

  @Override
  public Optional<DirectBuffer> getBpmnProcessIdForProcessInstance(final long key) {
    ensureServiceIsOpened();

    return Optional.ofNullable(state.getElementInstanceState().getInstance(key))
        .map(ElementInstance::getValue)
        .map(ProcessInstanceRecord::getBpmnProcessIdBuffer);
  }

  @Override
  public Optional<DirectBuffer> getBpmnProcessIdForJob(final long key) {
    ensureServiceIsOpened();

    return Optional.ofNullable(state.getJobState().getJob(key))
        .map(JobRecord::getBpmnProcessIdBuffer);
  }

  private void ensureServiceIsOpened() {
    if (isClosed) {
      throw new ClosedServiceException();
    }
    if (state == null) {
      // service is used for the first time, create state now
      // we don't need a key generator here, so we set it to unsupported
      state =
          new ProcessingDbState(
              Protocol.DEPLOYMENT_PARTITION,
              zeebeDb,
              zeebeDb.createContext(),
              KeyGenerator.immutable(),
              new TransientPendingSubscriptionState(),
              new TransientPendingSubscriptionState(),
              new EngineConfiguration(),
              clock);
    }
  }
}
