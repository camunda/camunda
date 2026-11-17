/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;

public final class AgentDefinitionBehavior {

  private final ProcessState processState;

  public AgentDefinitionBehavior(final ProcessState processState) {
    this.processState = processState;
  }

  /**
   * Answers whether the given job belongs to an agent, i.e. whether the job's element was marked as
   * an agent definition at deployment time. It is cheap enough to call on every job cancellation,
   * completion, and thrown error.
   *
   * <p>A job's element id does not always refer to a real BPMN element: for example, {@link
   * io.camunda.zeebe.engine.processing.job.JobThrowErrorProcessor} overwrites it with a synthetic
   * marker when an error is thrown but not caught. That case can't have an agent definition, so it
   * resolves to {@code false} rather than raising.
   *
   * <p>The process lookup is defensive rather than a known reachable case: process deletion drains
   * until every active instance finishes, so a job on a running instance always finds its process
   * in state. Resolving to {@code false} here guards against that invariant changing, rather than
   * documenting a gap that exists today.
   */
  public boolean belongsToAgent(final JobRecord job) {
    final var process =
        processState.getProcessByKeyAndTenant(job.getProcessDefinitionKey(), job.getTenantId());
    if (process == null) {
      return false;
    }

    final ExecutableFlowElement element =
        process.getProcess().getElementById(job.getElementIdBuffer());
    if (element == null) {
      return false;
    }

    // A multi-instance body and its inner activity share the same element id, but only the inner
    // activity can carry the agent-definition marker. Resolve to it here, or a job on a
    // multi-instance agent element would look like it doesn't belong to an agent.
    final ExecutableFlowElement agentDefinitionElement =
        element instanceof final ExecutableMultiInstanceBody multiInstanceBody
            ? multiInstanceBody.getInnerActivity()
            : element;

    return agentDefinitionElement instanceof final ExecutableJobWorkerElement jobWorkerElement
        && jobWorkerElement.isAgentDefinition();
  }
}
