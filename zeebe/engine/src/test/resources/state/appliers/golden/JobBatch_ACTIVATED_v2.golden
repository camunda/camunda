/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableJobBatchDeliveryState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Activates jobs like {@link JobBatchActivatedApplier} and, when a delivery attempt key is present,
 * registers a pending delivery so the gateway handshake can ACK or REJECT the batch.
 */
public final class JobBatchActivatedV2Applier
    implements TypedEventApplier<JobBatchIntent, JobBatchRecord> {

  private final MutableJobState jobState;
  private final MutableJobBatchDeliveryState jobBatchDeliveryState;

  public JobBatchActivatedV2Applier(final MutableProcessingState state) {
    jobState = state.getJobState();
    jobBatchDeliveryState = state.getJobBatchDeliveryState();
  }

  @Override
  public void applyState(final long key, final JobBatchRecord value) {
    final Iterator<JobRecord> iterator = value.jobs().iterator();
    final Iterator<LongValue> keyIt = value.jobKeys().iterator();
    final List<Long> activatedJobKeys = new ArrayList<>();
    while (iterator.hasNext() && keyIt.hasNext()) {
      final JobRecord jobRecord = iterator.next();
      final long jobKey = keyIt.next().getValue();
      jobState.activate(jobKey, jobRecord);
      activatedJobKeys.add(jobKey);
    }

    final long deliveryAttemptKey = value.getDeliveryAttemptKey();
    final long deliveryDeadline = value.getDeliveryDeadline();
    if (deliveryAttemptKey > 0 && deliveryDeadline > 0 && !activatedJobKeys.isEmpty()) {
      jobBatchDeliveryState.storePendingDelivery(
          deliveryAttemptKey, value.getType(), deliveryDeadline, activatedJobKeys);
    }
  }
}
