/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processdefinition;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ProcessDefinitionResumeProcessor
    extends AbstractProcessDefinitionSuspensionProcessor {

  public ProcessDefinitionResumeProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final CslTenantCheck tenantCheck,
      final CommandDistributionBehavior commandDistributionBehavior) {
    super(
        "resume",
        ProcessDefinitionSuspensionIntent.RESUMED,
        writers,
        keyGenerator,
        processingState,
        tenantCheck,
        commandDistributionBehavior);
  }

  @Override
  @Nullable String validateMarker(final long processDefinitionKey, final boolean suspended) {
    return suspended
        ? null
        : "Expected to resume process definition with key '%d', but it is not suspended"
            .formatted(processDefinitionKey);
  }
}
