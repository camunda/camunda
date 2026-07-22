/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.util.ArrayList;
import java.util.List;

public class JobCanceledV4Applier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;
  private final MutableElementInstanceState elementInstanceState;
  private final MutableSecretReferenceState secretReferenceState;

  JobCanceledV4Applier(final MutableProcessingState state) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    secretReferenceState = state.getSecretReferenceState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    jobState.cancel(key, value);
    removeWaitingSecretReferenceEntries(key);

    if (value.isJobToUserTaskMigration()) {
      final var elementInstance = elementInstanceState.getInstance(value.getElementInstanceKey());
      if (elementInstance != null) {
        elementInstance.setJobKey(-1L);
        elementInstanceState.updateInstance(elementInstance);
      }
    }
  }

  /**
   * Removes the waiting entries of a job parked for secret resolution (see {@link
   * SecretReferenceResolutionRequestedApplier}), so the canceled job is not reactivated and gets no
   * incident once the resolution completes or fails. The pending secret reference itself is kept:
   * the background resolution removes it through its own terminal events.
   */
  private void removeWaitingSecretReferenceEntries(final long jobKey) {
    // collect first: the column family must not be modified while iterating it
    final List<WaitingEntry> waitingEntries = new ArrayList<>();
    secretReferenceState.visitSecretReferencesByJob(
        jobKey,
        (storeId, secretReference) -> {
          waitingEntries.add(new WaitingEntry(storeId, secretReference));
          return true;
        });
    for (final WaitingEntry entry : waitingEntries) {
      secretReferenceState.removeWaitingJob(entry.storeId(), entry.secretReference(), jobKey);
    }
  }

  /** A secret reference the canceled job was waiting for. */
  private record WaitingEntry(String storeId, String secretReference) {}
}
