/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;

/**
 * Also keeps track of a request awaiting the instance's result on every replica, so that the result
 * can be sent after leadership of the partition moved.
 */
final class ProcessInstanceCreationCreatedV3Applier
    implements TypedEventApplier<ProcessInstanceCreationIntent, ProcessInstanceCreationRecord> {

  private final ProcessInstanceCreationCreatedV2Applier v2Applier;
  private final MutableElementInstanceState elementInstanceState;
  private final AsyncRequestState asyncRequestState;

  ProcessInstanceCreationCreatedV3Applier(
      final MutableProcessState processState,
      final MutableElementInstanceState elementInstanceState,
      final MutableUsageMetricState usageMetricState,
      final AsyncRequestState asyncRequestState) {
    v2Applier =
        new ProcessInstanceCreationCreatedV2Applier(
            processState, elementInstanceState, usageMetricState);
    this.elementInstanceState = elementInstanceState;
    this.asyncRequestState = asyncRequestState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceCreationRecord value) {
    v2Applier.applyState(key, value);

    final var processInstanceKey = value.getProcessInstanceKey();
    asyncRequestState
        .findRequest(
            processInstanceKey,
            ValueType.PROCESS_INSTANCE_CREATION,
            ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT)
        .ifPresent(
            request ->
                elementInstanceState.setAwaitResultRequestMetadata(
                    processInstanceKey,
                    new AwaitProcessInstanceResultMetadata()
                        .setRequestId(request.requestId())
                        .setRequestStreamId(request.requestStreamId())
                        .setFetchVariables(value.fetchVariables())));
  }
}
