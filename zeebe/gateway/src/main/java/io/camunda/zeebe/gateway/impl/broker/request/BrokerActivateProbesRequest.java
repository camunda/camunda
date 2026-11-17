/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProbeBatchIntent;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A worker's poll for probes of its probe IDs on one partition, as {@link
 * BrokerActivateJobsRequest} for jobs. The long polling handler sends it to every partition.
 */
@NullMarked
public final class BrokerActivateProbesRequest extends BrokerExecuteCommand<ProbeBatchRecord> {

  private final ProbeBatchRecord requestDto = new ProbeBatchRecord();

  public BrokerActivateProbesRequest(final List<String> probeIds) {
    super(ValueType.PROBE_BATCH, ProbeBatchIntent.ACTIVATE);
    requestDto.setProbeIds(probeIds);
  }

  public BrokerActivateProbesRequest setWorker(final @Nullable String worker) {
    if (worker != null) {
      requestDto.setWorker(worker);
    }
    return this;
  }

  public BrokerActivateProbesRequest setMaxProbesToActivate(final int maxProbesToActivate) {
    requestDto.setMaxProbesToActivate(maxProbesToActivate);
    return this;
  }

  public BrokerActivateProbesRequest setTenantIds(final @Nullable List<String> tenantIds) {
    if (tenantIds != null) {
      requestDto.setTenantIds(tenantIds);
    }
    return this;
  }

  public BrokerActivateProbesRequest setTenantFilter(final @Nullable TenantFilter tenantFilter) {
    if (tenantFilter != null) {
      requestDto.setTenantFilter(tenantFilter);
    }
    return this;
  }

  @Override
  public ProbeBatchRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ProbeBatchRecord toResponseDto(final DirectBuffer buffer) {
    final var responseDto = new ProbeBatchRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
