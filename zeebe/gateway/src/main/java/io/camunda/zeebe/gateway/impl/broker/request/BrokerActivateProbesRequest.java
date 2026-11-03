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
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.List;
import org.agrona.DirectBuffer;

/**
 * A worker's poll for the probes of a probe ID on one partition, as {@code
 * BrokerActivateJobsRequest} for jobs. The long polling handler sends it to every partition.
 */
public final class BrokerActivateProbesRequest extends BrokerExecuteCommand<ProbeBatchRecord> {

  private final ProbeBatchRecord requestDto = new ProbeBatchRecord();

  public BrokerActivateProbesRequest(
      final String probeId,
      final String worker,
      final int maxProbesToActivate,
      final List<String> tenantIds,
      final TenantFilter tenantFilter) {
    super(ValueType.PROBE_BATCH, ProbeBatchIntent.ACTIVATE);
    requestDto
        .setProbeId(probeId)
        .setWorker(worker)
        .setMaxProbesToActivate(maxProbesToActivate)
        .setTenantIds(tenantIds)
        .setTenantFilter(tenantFilter);
  }

  public String getProbeId() {
    return requestDto.getProbeId();
  }

  public String getWorker() {
    return requestDto.getWorker();
  }

  public List<String> getTenantIds() {
    return requestDto.getTenantIds();
  }

  public TenantFilter getTenantFilter() {
    return requestDto.getTenantFilter();
  }

  public int getMaxProbesToActivate() {
    return requestDto.getMaxProbesToActivate();
  }

  public BrokerActivateProbesRequest setMaxProbesToActivate(final int maxProbesToActivate) {
    requestDto.setMaxProbesToActivate(maxProbesToActivate);
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ProbeBatchRecord toResponseDto(final DirectBuffer buffer) {
    final var responseDto = new ProbeBatchRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
