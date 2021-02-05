/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableJobState;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.JobIntent;

final class JobCreatedApplier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableJobState jobState;

  JobCreatedApplier(final ZeebeState state) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    jobState.create(key, value);

    final long elementInstanceKey = value.getElementInstanceKey();
    if (elementInstanceKey > 0) {
      final ElementInstance elementInstance = elementInstanceState.getInstance(elementInstanceKey);

      if (elementInstance != null) {
        elementInstance.setJobKey(key);
        elementInstanceState.updateInstance(elementInstance);
      }
    }
  }
}
