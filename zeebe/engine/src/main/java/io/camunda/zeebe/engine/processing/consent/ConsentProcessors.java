/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.consent;

import io.camunda.zeebe.engine.metrics.IncidentMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ConsentIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public final class ConsentProcessors {

  private ConsentProcessors() {}

  public static void addConsentProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final ProcessingState state,
      final BpmnBehaviors bpmnBehaviors,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final PermissionsBehavior permissionsBehavior,
      final IncidentMetrics incidentMetrics) {
    final var jobActivationBehavior = bpmnBehaviors.jobActivationBehavior();
    final var connections = jobActivationBehavior.consentGate().connections();
    typedRecordProcessors
        .onCommand(
            ValueType.CONSENT,
            ConsentIntent.GRANT,
            new ConsentGrantProcessor(
                state.getConsentState(), connections, keyGenerator, writers, distributionBehavior))
        .onCommand(
            ValueType.CONSENT,
            ConsentIntent.REVOKE,
            new ConsentRevokeProcessor(
                state.getConsentState(), permissionsBehavior, writers, distributionBehavior))
        .onCommand(
            ValueType.CONSENT,
            ConsentIntent.RESOLVE,
            new ConsentResolveProcessor(
                state,
                bpmnBehaviors.eventPublicationBehavior(),
                jobActivationBehavior,
                keyGenerator,
                writers,
                incidentMetrics))
        .onCommand(
            ValueType.CONSENT,
            ConsentIntent.RELEASE,
            new ConsentReleaseProcessor(state, jobActivationBehavior, keyGenerator, writers));
  }
}
