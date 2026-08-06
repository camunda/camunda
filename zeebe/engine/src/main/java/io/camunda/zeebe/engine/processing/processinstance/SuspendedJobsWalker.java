/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.ArrayDeque;
import java.util.function.BiConsumer;
import org.jspecify.annotations.NullMarked;

/**
 * Visits the jobs of a single process instance that are in a given job state.
 *
 * <p>The walk descends the element instance tree of the instance but stops at call activity
 * boundaries: a suspension marker is keyed by the suspended instance alone, so commands of a called
 * child instance are not gated and would un-park its jobs again.
 */
@NullMarked
public final class SuspendedJobsWalker {

  private final ElementInstanceState elementInstanceState;
  private final JobState jobState;

  public SuspendedJobsWalker(
      final ElementInstanceState elementInstanceState, final JobState jobState) {
    this.elementInstanceState = elementInstanceState;
    this.jobState = jobState;
  }

  public void forEachJob(
      final long processInstanceKey,
      final State state,
      final BiConsumer<Long, JobRecord> consumer) {
    final var processInstance = elementInstanceState.getInstance(processInstanceKey);
    if (processInstance == null) {
      return;
    }

    // a queue instead of recursion, to not blow the stack on a deeply nested instance
    final var elementInstances = new ArrayDeque<ElementInstance>();
    elementInstances.add(processInstance);
    while (!elementInstances.isEmpty()) {
      final var elementInstance = elementInstances.poll();
      visitJob(elementInstance, state, consumer);
      elementInstanceState.getChildren(elementInstance.getKey()).stream()
          .filter(child -> child.getValue().getProcessInstanceKey() == processInstanceKey)
          .forEach(elementInstances::add);
    }
  }

  private void visitJob(
      final ElementInstance elementInstance,
      final State state,
      final BiConsumer<Long, JobRecord> consumer) {
    final long jobKey = elementInstance.getJobKey();
    if (jobKey <= 0 || jobState.getState(jobKey) != state) {
      return;
    }
    final var job = jobState.getJob(jobKey);
    if (job != null) {
      consumer.accept(jobKey, job);
    }
  }
}
